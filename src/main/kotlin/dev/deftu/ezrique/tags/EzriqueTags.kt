package dev.deftu.ezrique.tags

import dev.deftu.ezrique.*
import dev.deftu.ezrique.tags.commands.CommandDelegator
import dev.deftu.ezrique.tags.sql.TagTable
import dev.deftu.ezrique.tags.utils.Healthchecks
import dev.deftu.ezrique.tags.utils.scheduleAtFixedRate
import dev.deftu.ezrique.tags.utils.isInDocker
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.gateway.DisconnectEvent
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.gateway.ResumedEvent
import dev.kord.core.event.interaction.*
import dev.kord.core.on
import dev.kord.gateway.Intents
import dev.kord.gateway.NON_PRIVILEGED
import dev.kord.gateway.builder.PresenceBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.count
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.TimeUnit

object EzriqueTags {

    const val NAME = "@PROJECT_NAME@"
    const val VERSION = "@PROJECT_VERSION@"
    private val LOGGER: Logger = LogManager.getLogger(NAME)

    private val sentryUrl: String?
        get() {
            var sentryUrl = System.getenv("SENTRY_URL")
            if (sentryUrl == null || sentryUrl.isEmpty()) {
                sentryUrl = config.get("sentry_url")?.asString
            }

            return sentryUrl
        }

    private val dbUrl: String
        get() {
            var dbUrl = System.getenv("DATABASE_URL")
            if (dbUrl == null || dbUrl.isEmpty()) {
                dbUrl = config.get("database_url")?.asString
                if (dbUrl == null || dbUrl.isEmpty()) error("No DB URL provided!")
            }

            return dbUrl
        }

    private val dbPassword: String
        get() {
            var dbPassword = System.getenv("DATABASE_PASSWORD")
            if (dbPassword == null || dbPassword.isEmpty()) {
                dbPassword = config.get("database_password")?.asString
                if (dbPassword == null || dbPassword.isEmpty()) error("No DB password provided!")
            }

            return dbPassword
        }

    private lateinit var kord: Kord

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        try {
            LOGGER.info("Starting $NAME v$VERSION")

            initializeSentry()

            if (!initializeDatabase()) {
                return@runBlocking
            }

            if (!initializeKord()) {
                return@runBlocking
            }

            setupHealthchecks()
            setupKordListeners()
            setupInteractionListeners()

            kord.createGlobalApplicationCommands {
                CommandDelegator.setupGlobalCommands(this)
            }

            val guildCommandSetupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            guildCommandSetupScope.launch {
                CommandDelegator.setupGuildCommands(kord)
            }

            try {
                kord.login {
                    intents {
                        +Intents.NON_PRIVILEGED
                    }
                }
            } catch (t: Throwable) {
                handleError(t, TagsErrorCode.KORD_LOGIN)
                LOGGER.error("An error occurred while logging in", t)
            }
        } catch (t: Throwable) {
            handleError(t, TagsErrorCode.UNKNOWN)
            LOGGER.error("An unknown error occurred", t)
        }
    }

    private fun initializeSentry() {
        val sentryUrl = this.sentryUrl
        if (sentryUrl == null) {
            LOGGER.warn("No Sentry URL provided, skipping Sentry setup")
            return
        }

        LOGGER.info("Setting up Sentry")
        setupSentry(sentryUrl, NAME, VERSION)
    }

    private suspend fun initializeDatabase(): Boolean {
        return try {
            LOGGER.info("Setting up database (url: '$dbUrl', passed url: 'jdbc:postgresql://$dbUrl', pass: '${dbPassword.take(4) + "*".repeat(dbPassword.length - 4)}')")
            Database.connect(
                driver = "org.postgresql.Driver",
                databaseConfig = DatabaseConfig {
                    useNestedTransactions = true
                },

                url = "jdbc:postgresql://$dbUrl",

                user = "ezrique_tags",
                password = dbPassword
            )

            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    TagTable
                )
            }

            LOGGER.info("Connected to database")
            true
        } catch (e: Exception) {
            handleError(e, null)
            false // Don't continue if the database isn't set up
        }
    }

    private suspend fun initializeKord(): Boolean {
        return try {
            LOGGER.info("Setting up Kord")
            kord = Kord(token) {
                setup()
            }

            true
        } catch (e: Exception) {
            handleError(e, null)
            false // Don't continue if Kord isn't set up
        }
    }

    private fun setupHealthchecks() {
        kord.on<ReadyEvent> {
            if (isInDocker()) {
                LOGGER.info("Started healthcheck server")

                // Set up healthcheck HTTP server
                Healthchecks.start()
            }
        }

        kord.on<ResumedEvent> {
            if (isInDocker()) {
                LOGGER.info("Resumed connection to Discord - starting healthcheck server")

                // Set up healthcheck HTTP server
                Healthchecks.start()
            }
        }

        kord.on<DisconnectEvent> {
            if (isInDocker()) {
                LOGGER.info("Stopped healthcheck server - Kord disconnect")

                // Close healthcheck HTTP server
                Healthchecks.close()
            }
        }
    }

    private fun setupKordListeners() {
        val presenceContext = Dispatchers.Default + SupervisorJob()

        /**
         * Responsible for printing the bot's tag to the console when it logs in
         */
        kord.on<ReadyEvent> {
            LOGGER.info("Logged in as ${kord.getSelf().tag}")

        }

        kord.on<ResumedEvent> {
            LOGGER.info("Resumed connection to Discord")
        }

        /**
         * Responsible for printing the bot's tag to the console when it logs out
         */
        kord.on<DisconnectEvent> {
            LOGGER.error("Disconnected from Discord")
        }

        /**
         * Responsible for managing the bot's presence
         */
        kord.on<ReadyEvent> {
            val possiblePresences = setOf<suspend PresenceBuilder.(kord: Kord) -> Unit>(
                { kord ->
                    // Guild count
                    var count = 0
                    kord.guilds.collect { count++ }

                    listening("to silly little tags in $count guilds")
                },
                { kord ->
                    // User count
                    var count = 0
                    kord.guilds.collect { guild ->
                        count += guild.memberCount ?: guild.members.count() // If we don't know the actual member count, just approximate it from the members we know of
                    }

                    watching("over approximately $count people's tickets!")
                }
            )

            var lastPresenceIndex = 0

            val scope = CoroutineScope(presenceContext)
            scope.scheduleAtFixedRate(TimeUnit.SECONDS, 0, 30) {
                runBlocking {
                    val presenceIndex = (lastPresenceIndex + 1) % possiblePresences.size
                    lastPresenceIndex = presenceIndex

                    val presence = possiblePresences.elementAt(presenceIndex)
                    kord.editPresence {
                        // Apply the rotated presence
                        presence(kord)

                        // Every presence change, flip between ONLINE and DND
                        status = if (presenceIndex % 2 == 0) PresenceStatus.Online else PresenceStatus.DoNotDisturb
                    }
                }
            }
        }
    }

    private fun setupInteractionListeners() {
        /**
         * Tells the user that the bot is only available in guilds
         */
        kord.on<GlobalChatInputCommandInteractionCreateEvent> {
            interaction.respondEphemeral {
                stateEmbed(EmbedState.ERROR) {
                    description = "This bot is only available in guilds."
                }
            }
        }

        kord.on<GuildChatInputCommandInteractionCreateEvent> {
            try {
                val (rootName, subCommandName, _) = interaction.command.names
                CommandDelegator.handleCommand(this, rootName, subCommandName)
            } catch (e: Exception) {
                handleError(e, TagsErrorCode.UNKNOWN_COMMAND)
            }
        }

        kord.on<GuildAutoCompleteInteractionCreateEvent> {
            try {
                val (rootName, subCommandName, _) = interaction.command.names
                CommandDelegator.handleAutoComplete(this, rootName, subCommandName)
            } catch (e: Exception) {
                handleError(e, TagsErrorCode.UNKNOWN_COMMAND)
            }
        }

        kord.on<GuildModalSubmitInteractionCreateEvent> {
            try {
                CommandDelegator.handleModal(this)
            } catch (e: Exception) {
                handleError(e, TagsErrorCode.UNKNOWN_COMMAND)
            }
        }
    }

}
