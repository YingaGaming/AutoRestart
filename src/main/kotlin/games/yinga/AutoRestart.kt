// Copyright (C) 2023 Marcus Huber (xenorio) <dev@xenorio.xyz>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package games.yinga

import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.ChatColor

class AutoRestart : JavaPlugin() {
    private lateinit var config: FileConfiguration
    private var lastConfigModified: Long = 0
    private var scheduledTask: Int = -1
    private val notificationTasks = mutableListOf<Int>()

    override fun onEnable() {
        logger.info("AutoRestart has been enabled!")

        // Save the default configuration if it doesn't exist
        saveDefaultConfig()

        // Load the configuration
        config = getConfig()

        // Schedule the initial server shutdown
        scheduleServerShutdown()

        // Start a task to check for config changes
        object : BukkitRunnable() {
            override fun run() {
                val configFile = File(dataFolder, "config.yml")
                if (configFile.lastModified() > lastConfigModified) {
                    lastConfigModified = configFile.lastModified()
                    reloadConfig()
                    config = getConfig()
                    // Config file has changed, reschedule the shutdown
                    rescheduleServerShutdown()
                }
            }
        }
            .runTaskTimer(this, 20L, 20L) // Check every second (20 ticks)
    }

    override fun onDisable() {
        logger.info("AutoRestart has been disabled!")
    }

    private fun rescheduleServerShutdown() {
        // Cancel the previously scheduled shutdown if it exists
        if (scheduledTask != -1) {
            server.scheduler.cancelTask(scheduledTask)
        }

        // Cancel outstanding notifications
        notificationTasks.forEach {
            server.scheduler.cancelTask(it)
        }

        // Schedule the server shutdown with the updated time
        scheduleServerShutdown()
    }

    private fun scheduleServerShutdown() {
        val shutdownTimeStr = config.getString("time", "00:00:00")

        // Parse the time from the config
        val sdf = SimpleDateFormat("HH:mm:ss")
        val currentTime = sdf.format(Date())
        val currentTimeDate = sdf.parse(currentTime)

        val shutdownTime = sdf.parse(shutdownTimeStr)

        // Calculate the time difference between the configured time and the current time
        val timeDifference = shutdownTime.time - currentTimeDate.time

        if (timeDifference <= 0) {
            // If the shutdown time is in the past, add a day's worth of time
            val fullDayMillis = 24 * 60 * 60 * 1000
            shutdownTime.time += fullDayMillis
        }

        // Calculate the remaining time
        val remainingTimeMillis = shutdownTime.time - currentTimeDate.time

        // Calculate the delay until the shutdown time
        val delay = remainingTimeMillis
        // Convert to server ticks (20 ticks per second)
        val delayTicks = (delay / 1000 * 20).toInt()

        var title = config.getString("title", "")
        var subtitle = config.getString("subtitle", "")

        // Schedule notifications
        config.getIntegerList("notifications").forEach {
            val task = object : BukkitRunnable() {
                override fun run() {
                    var parsedTitle = title!!.replace("{REMAINING}", formatRemainingTime(it))
                    var parsedSubtitle = subtitle!!.replace("{REMAINING}", formatRemainingTime(it))
                    parsedTitle = ChatColor.translateAlternateColorCodes('&', parsedTitle)
                    parsedSubtitle = ChatColor.translateAlternateColorCodes('&', parsedSubtitle)
                    notifyPlayersWithTitle(parsedTitle, parsedSubtitle)
                }
            }
                .runTaskLater(
                    this, delayTicks - (it.toLong() * 20) - 10
                ).taskId

            notificationTasks.add(task)
        }

        logger.info("Server shutdown scheduled for ${sdf.format(shutdownTime)}")

        // Schedule the server shutdown
        scheduledTask =
            object : BukkitRunnable() {
                override fun run() {
                    logger.info("Shutting down the server as scheduled...")
                    server.shutdown()
                }
            }
                .runTaskLater(this, delayTicks.toLong())
                .taskId
    }

    private fun notifyPlayersWithTitle(title: String, subtitle: String) {
        for (player in server.onlinePlayers) {
            player.sendTitle(title, subtitle, 10, 70, 20)
        }
    }

    private fun formatRemainingTime(remainingTime: Int): String {
        val minutes = remainingTime / 60
        val seconds = remainingTime % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

