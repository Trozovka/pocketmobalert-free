package com.trozovka.pocketmobalert.core

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ButtonDefaults
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trozovka.pocketmobalert.core.beacon.MobAlertBeaconService
import com.trozovka.pocketmobalert.core.ble.BlePermissionFlow
import com.trozovka.pocketmobalert.core.data.AlertLogEntity
import com.trozovka.pocketmobalert.core.data.PocketMobAlertDatabase
import com.trozovka.pocketmobalert.core.entitlement.EntitlementManager
import com.trozovka.pocketmobalert.core.entitlement.EntitlementManagerHolder
import com.trozovka.pocketmobalert.core.opencpn.OpenCpnSettings
import com.trozovka.pocketmobalert.core.watch.DeviceAlarmState
import com.trozovka.pocketmobalert.core.watch.MobAlertWatchService
import com.trozovka.pocketmobalert.core.watch.PairedCrewStore
import com.trozovka.toolkit.reliability.LocationReliabilityPermissionFlow
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var blePermissionFlow: BlePermissionFlow
    private lateinit var locationPermissionFlow: LocationReliabilityPermissionFlow
    private var pendingServiceToStart: (() -> Unit)? = null

    private fun showNotice(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationPermissionFlow = LocationReliabilityPermissionFlow(
            activity = this,
            onReady = { pendingServiceToStart?.invoke() },
            onDenied = { showNotice("Location permission was not allowed. Watch Mode needs it to find Bluetooth signals and to save where an alarm happened. Allow it in Settings > Apps > this app > Permissions.") },
        )
        blePermissionFlow = BlePermissionFlow(
            activity = this,
            onReady = { locationPermissionFlow.begin() },
            onDenied = { showNotice("Nearby devices (Bluetooth) permission was not allowed. The app cannot work without it. Allow it in Settings > Apps > this app > Permissions.") },
        )

        val entitlementManager = (application as EntitlementManagerHolder).entitlementManager
        val pairedCrewStore = PairedCrewStore(applicationContext)

        // Light status-bar icons on the dark teal bar.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            TrozovkaTheme {
                Scaffold(containerColor = Slate, contentWindowInsets = WindowInsets(0)) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
                        AppTabs(
                            onStartCrewMode = {
                                pendingServiceToStart = { startService(Intent(this@MainActivity, MobAlertBeaconService::class.java)) }
                                blePermissionFlow.begin()
                            },
                            onStopCrewMode = { stopService(Intent(this@MainActivity, MobAlertBeaconService::class.java)) },
                            onStartWatchMode = {
                                pendingServiceToStart = { startService(Intent(this@MainActivity, MobAlertWatchService::class.java)) }
                                blePermissionFlow.begin()
                            },
                            onStopWatchMode = { stopService(Intent(this@MainActivity, MobAlertWatchService::class.java)) },
                            onAcknowledge = { deviceId ->
                                startService(
                                    Intent(this@MainActivity, MobAlertWatchService::class.java)
                                        .setAction(MobAlertWatchService.ACTION_ACKNOWLEDGE)
                                        .putExtra(MobAlertWatchService.EXTRA_DEVICE_ID, deviceId),
                                )
                            },
                            onAcknowledgeRelay = {
                                startService(
                                    Intent(this@MainActivity, MobAlertWatchService::class.java)
                                        .setAction(MobAlertWatchService.ACTION_ACKNOWLEDGE_RELAY),
                                )
                            },
                            pairedCrewStore = pairedCrewStore,
                            entitlementManager = entitlementManager,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTabs(
    onStartCrewMode: () -> Unit,
    onStopCrewMode: () -> Unit,
    onStartWatchMode: () -> Unit,
    onStopWatchMode: () -> Unit,
    onAcknowledge: (String) -> Unit,
    onAcknowledgeRelay: () -> Unit,
    pairedCrewStore: PairedCrewStore,
    entitlementManager: EntitlementManager,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val appLabel = remember { context.packageManager.getApplicationLabel(context.applicationInfo).toString() }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    // 0 = main screen, 1 = Settings, 2 = How to use (opened from Settings)
    var page by remember { mutableIntStateOf(0) }
    if (page == 2) {
        DocumentationScreen(appLabel, versionName, onClose = { page = 1 })
        return
    }
    if (page == 1) {
        SettingsScreen(appLabel, versionName, onHowTo = { page = 2 }, onClose = { page = 0 })
        return
    }

    Column {
        // Teal top bar (behind the status bar too): app name, version, Settings gear.
        Box(modifier = Modifier.fillMaxWidth().background(TealBar).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(appLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("v${versionName ?: "?"}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = { page = 1 }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
        TabRow(selectedTabIndex = selectedTab, containerColor = TealBar, contentColor = Color.White) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Crew Mode") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Watch Mode") })
        }
        LicenseSection(entitlementManager)
        when (selectedTab) {
            0 -> CrewModeScreen(onStartCrewMode, onStopCrewMode)
            1 -> WatchModeScreen(
                onStartWatchMode, onStopWatchMode, onAcknowledge, onAcknowledgeRelay,
                pairedCrewStore, entitlementManager,
            )
        }
    }
}

@Composable
private fun LicenseSection(entitlementManager: EntitlementManager) {
    // Free tier has no license concept at all -- entitlementManager.licenseStatusMessage()
    // returns null and this section renders nothing, rather than an empty/irrelevant box.
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var keyOverride by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        statusMessage = entitlementManager.licenseStatusMessage()
    }

    val message = statusMessage ?: return
    val keyText = keyOverride ?: entitlementManager.getLicenseKeyInput()

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        // Store builds without a key box show their own plain sentence, no "License:" label.
        Text(if (entitlementManager.usesLicenseKeyField()) "License: $message" else message)
        if (entitlementManager.usesLicenseKeyField()) {
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyOverride = it },
                label = { Text("Gumroad license key") },
            )
        }
        Button(onClick = {
            scope.launch {
                entitlementManager.setLicenseKeyAndVerify(keyText)
                statusMessage = entitlementManager.licenseStatusMessage()
                // time stamp so a repeated tap with the same result still visibly answers
                actionMessage = entitlementManager.lastActionMessage()?.let { "$it (${nowText()})" }
            }
        }) { Text(entitlementManager.activateButtonLabel()) }
        actionMessage?.let { Text(it) }
    }
}

@Composable
private fun CrewModeScreen(onStartCrewMode: () -> Unit, onStopCrewMode: () -> Unit) {
    val status by MobAlertBeaconService.status.collectAsState()
    val deviceId by MobAlertBeaconService.deviceIdHex.collectAsState()

    var lastTap by remember { mutableStateOf<String?>(null) }
    val sending = status == MobAlertBeaconService.BeaconStatus.Advertising

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Use Crew Mode on the phone of the person going out on deck. It sends a small Bluetooth signal. Your buddy's phone (Watch Mode) listens for it.")
        Text(
            when (val st = status) {
                MobAlertBeaconService.BeaconStatus.Stopped -> "Crew Mode: OFF"
                MobAlertBeaconService.BeaconStatus.Advertising -> "Crew Mode: ON, sending signal"
                is MobAlertBeaconService.BeaconStatus.Error -> "Crew Mode: OFF. Problem: ${st.message}${hintFor(st.message)}"
            },
            fontWeight = FontWeight.Bold,
        )
        if (sending) deviceId?.let { Text("This phone's ID: $it (your buddy sees this ID when pairing)") }
        Button(enabled = !sending, onClick = { lastTap = "Start tapped at ${nowText()}"; onStartCrewMode() }) { Text("Start Crew Mode") }
        Button(enabled = sending, colors = ButtonDefaults.buttonColors(containerColor = Orange), onClick = { lastTap = "Stop tapped at ${nowText()}"; onStopCrewMode() }) { Text("Stop Crew Mode") }
        lastTap?.let { Text(it) }
    }
}

@Composable
private fun WatchModeScreen(
    onStartWatchMode: () -> Unit,
    onStopWatchMode: () -> Unit,
    onAcknowledge: (String) -> Unit,
    onAcknowledgeRelay: () -> Unit,
    pairedCrewStore: PairedCrewStore,
    entitlementManager: EntitlementManager,
) {
    val sightings by MobAlertWatchService.sightings.collectAsState()
    val alarmStates by MobAlertWatchService.alarmStates.collectAsState()
    val relayedAlertActive by MobAlertWatchService.relayedAlertActive.collectAsState()
    val scanError by MobAlertWatchService.scanError.collectAsState()
    val running by MobAlertWatchService.running.collectAsState()
    var lastTap by remember { mutableStateOf<String?>(null) }

    // alarmStates ticks every ~1s while Watch mode runs, which doubles as this screen's refresh
    // cadence for the paired list (SharedPreferences-backed, not itself a reactive flow) and the
    // alert log (Room, not a reactive flow either).
    var pairedDevices by remember { mutableStateOf(pairedCrewStore.getAll()) }
    var maxCrewDevices by remember { mutableStateOf<Int?>(null) }
    var historyUnlocked by remember { mutableStateOf(false) }
    var openCpnUnlocked by remember { mutableStateOf(false) }
    var alertLog by remember { mutableStateOf<List<AlertLogEntity>>(emptyList()) }
    val context = LocalContext.current
    val openCpnSettings = remember { OpenCpnSettings(context) }
    LaunchedEffect(alarmStates) {
        pairedDevices = pairedCrewStore.getAll()
        maxCrewDevices = entitlementManager.maxPairedCrewDevices()
        historyUnlocked = entitlementManager.isHistoryAndExportUnlocked()
        openCpnUnlocked = entitlementManager.isOpenCpnIntegrationUnlocked()
        val dao = PocketMobAlertDatabase.getInstance(context).alertLogDao()
        alertLog = if (historyUnlocked) dao.getAll() else listOfNotNull(dao.getMostRecent())
    }

    val pairedIds = pairedDevices.map { it.deviceIdHex }.toSet()
    val unpairedSightings = sightings.filterKeys { it !in pairedIds }
    val atCapacity = maxCrewDevices?.let { pairedDevices.size >= it } ?: false

    // verticalScroll matters here specifically: this screen's content grows dynamically (paired
    // devices, alert log entries, an active alarm's extra Acknowledge button) and can genuinely
    // overflow the screen height -- without scroll support, content below the fold (e.g. the
    // OpenCPN section) becomes completely unreachable, not just visually cut off. Caught during
    // manual on-device testing, not by inspection.
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Use Watch Mode on your own phone. It listens for your buddy's Crew Mode signal and sounds the alarm if the signal is lost.")
        Text(
            when {
                running -> "Watch Mode: ON, listening"
                scanError != null -> "Watch Mode: OFF. Problem: $scanError${hintFor(scanError!!)}"
                else -> "Watch Mode: OFF"
            },
            fontWeight = FontWeight.Bold,
        )
        Button(enabled = !running, onClick = { lastTap = "Start tapped at ${nowText()}"; onStartWatchMode() }) { Text("Start Watch Mode") }
        Button(enabled = running, colors = ButtonDefaults.buttonColors(containerColor = Orange), onClick = { lastTap = "Stop tapped at ${nowText()}"; onStopWatchMode() }) { Text("Stop Watch Mode") }
        lastTap?.let { Text(it) }

        if (relayedAlertActive) {
            Text("ALARM relayed from another Watch device -- a paired crew member may be overboard.", color = Orange, fontWeight = FontWeight.Bold)
            Button(colors = ButtonDefaults.buttonColors(containerColor = Orange), onClick = onAcknowledgeRelay) { Text("Acknowledge Relayed Alarm") }
        }

        Text("Paired crew (${pairedDevices.size}${maxCrewDevices?.let { "/$it" } ?: ""}):")
        // Plain Column, not LazyColumn: this list is always small (free tier caps at 2, Pro
        // realistically a handful) and the whole screen is already one verticalScroll Column --
        // nesting a LazyColumn inside a scrolling Column crashes at runtime (infinite height
        // measurement constraint), caught by a real crash on-device, not by inspection.
        Column {
            pairedDevices.forEach { device ->
                val state = alarmStates[device.deviceIdHex] ?: DeviceAlarmState.Present
                PairedDeviceRow(
                    label = device.label,
                    state = state,
                    onRemove = {
                        pairedCrewStore.remove(device.deviceIdHex)
                        pairedDevices = pairedCrewStore.getAll()
                    },
                    onAcknowledge = { onAcknowledge(device.deviceIdHex) },
                )
            }
        }

        // Discovery/pairing UI only matters before you've paired your person -- this app is for
        // a specific small number of people watching each other's backs (typically just one),
        // not a roster to keep browsing once you're already set up.
        var pendingNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        if (pairedDevices.isEmpty()) {
            Text(
                when {
                    !running -> "To pair: start Watch Mode here and Crew Mode on your buddy's phone. Keep the phones close."
                    unpairedSightings.isEmpty() -> "Looking for Crew Mode phones nearby... none found yet. Start Crew Mode on your buddy's phone and keep it close."
                    else -> "Crew Mode phones found nearby. Type a name and tap Add Crew:"
                },
            )
            Column {
                unpairedSightings.values.forEach { sighting ->
                    Column {
                        Text("${sighting.deviceIdHex}  (${sighting.rssi} dBm)")
                        OutlinedTextField(
                            value = pendingNames[sighting.deviceIdHex] ?: "",
                            onValueChange = { pendingNames = pendingNames + (sighting.deviceIdHex to it) },
                            label = { Text("Name (e.g. Chief Engineer)") },
                        )
                        val typedName = pendingNames[sighting.deviceIdHex]?.trim().orEmpty()
                        Button(
                            enabled = !atCapacity && typedName.isNotEmpty(),
                            onClick = {
                                pairedCrewStore.add(sighting.deviceIdHex, typedName)
                                pairedDevices = pairedCrewStore.getAll()
                                pendingNames = pendingNames - sighting.deviceIdHex
                            },
                        ) { Text("Add Crew") }
                    }
                }
            }
        }

        Text(if (historyUnlocked) "Alert log:" else "Most recent alert (Pro unlocks full history):")
        Column {
            alertLog.forEach { entry ->
                val timestamp = DateFormat.getDateTimeInstance().format(Date(entry.timestampMillis))
                val position = if (entry.latitude != null && entry.longitude != null) {
                    "%.5f, %.5f".format(entry.latitude, entry.longitude)
                } else {
                    "position unavailable"
                }
                Text("${entry.crewLabel} -- $timestamp -- $position")
            }
        }

        if (openCpnUnlocked) {
            OpenCpnSettingsSection(openCpnSettings)
        }
    }
}

@Composable
private fun OpenCpnSettingsSection(settings: OpenCpnSettings) {
    var enabled by remember { mutableStateOf(settings.isEnabled) }
    // Not a plain `remember { mutableStateOf(settings.port.toString()) }` -- that reads the
    // persisted value only once at first composition, and this composable can leave/re-enter
    // composition (e.g. switching tabs away and back) before the entitlement check that gates it
    // resolves, discarding that remembered value. Falling back to the live persisted value
    // whenever there's no in-progress edit means the field is never stuck blank.
    var portOverride by remember { mutableStateOf<String?>(null) }
    val portText = portOverride ?: settings.port.toString()

    Column {
        Text("OpenCPN bonus: broadcast a \$WPL \"MOB\" waypoint on the local network when an " +
            "alarm fires. This only places a marked waypoint -- it does NOT trigger OpenCPN's " +
            "own MOB alarm/tracking mode.")
        Column {
            Text("Send to OpenCPN")
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    settings.isEnabled = it
                },
            )
        }
        OutlinedTextField(
            value = portText,
            onValueChange = { text ->
                portOverride = text
                text.toIntOrNull()?.let { settings.port = it }
            },
            label = { Text("OpenCPN UDP port (must match its network-connection settings)") },
        )
    }
}

@Composable
private fun PairedDeviceRow(
    label: String,
    state: DeviceAlarmState,
    onRemove: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    Column {
        val stateText = when (state) {
            DeviceAlarmState.WaitingForSignal -> "Waiting for signal... (start Crew Mode on this phone)"
            DeviceAlarmState.Present -> "Present"
            DeviceAlarmState.PossibleSeparation -> "Possible separation -- confirming..."
            DeviceAlarmState.Alarming -> "ALARM -- crew member may be overboard"
        }
        Text("$label: $stateText", color = if (state == DeviceAlarmState.Alarming) Orange else Color.Unspecified, fontWeight = if (state == DeviceAlarmState.Alarming) FontWeight.Bold else null)
        if (state == DeviceAlarmState.Alarming) {
            Button(colors = ButtonDefaults.buttonColors(containerColor = Orange), onClick = onAcknowledge) { Text("Acknowledge Alarm") }
        }
        Button(onClick = onRemove) { Text("Remove") }
    }
}

private fun nowText(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

private fun hintFor(problem: String): String = when {
    problem.contains("turned off", ignoreCase = true) -> ". Turn on Bluetooth, then tap Start again."
    problem.contains("permission", ignoreCase = true) -> ". Allow Nearby devices and Location in Settings > Apps > this app > Permissions."
    else -> ""
}

@Composable
private fun DocumentationScreen(appLabel: String, versionName: String?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(modifier = Modifier.fillMaxSize()) {
    ScreenBar("How to use", onClose)
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DocSection(
            "What it is",
            "A man-overboard alarm for two phones. You and one buddy each have a phone. " +
                "If your buddy falls overboard with the phone, the signal is lost and YOUR phone sounds the alarm. " +
                "It works fully offline: no internet, no SIM, no account.",
        )
        DocSection(
            "What you need",
            "Two Android phones with this app, Bluetooth ON on both, and the permissions the app asks for " +
                "(Nearby devices and Location).",
        )
        DocSection(
            "Who uses which mode",
            "Crew Mode = the phone of the person going out on deck (in the pocket).\n" +
                "Watch Mode = the phone of the buddy who stays and listens for the alarm.",
        )
        DocSection(
            "Step by step",
            "1. Buddy's phone: open the app, tab Crew Mode, tap Start Crew Mode. It shows ON.\n" +
                "2. Your phone: tab Watch Mode, tap Start Watch Mode. It shows ON.\n" +
                "3. Keep the phones close. Your buddy's phone appears in the list. Type a name (for example Bosun) and tap Add Crew.\n" +
                "4. Leave both running. You can lock the screens. (Started Watch first? It waits 30 seconds for your buddy's signal before it alarms.)\n" +
                "5. If the signal is lost, your phone sounds the alarm and saves the time (and the position, if the phone has one). It keeps ringing until you tap Acknowledge Alarm, even if the signal comes back, so you always go and check on your buddy.\n" +
                "6. Finished? Tap Stop on both phones.",
        )
        DocSection(
            "Before you rely on it",
            "Works best when you and your buddy work close together, within about 10-30 m (less behind steel). " +
                "Steel decks and walls block Bluetooth. Walk around your ship once and check that the alarm does NOT go off where your buddy normally works. " +
                "This app is an extra help only. It does not replace your lifejacket, PLB/AIS-MOB beacon, or the ship's man-overboard procedure.",
        )
        DocSection(
            "If something does not work",
            "\"Bluetooth is turned off\": turn on Bluetooth, tap Start again.\n" +
                "\"Permission not granted\": Settings > Apps > this app > Permissions, allow Nearby devices and Location.\n" +
                "Buddy not found: start Crew Mode on the other phone and hold the phones close.\n" +
                "Alarm goes off by itself: the phones are too far apart or behind steel. Move closer.\n" +
                "Turn off battery saver for this app so Android does not stop it.",
        )
        DocSection(
            "Free and Pro",
            "Free: the full alarm, up to 2 crew members, last alert saved.\n" +
                "Pro (one payment): unlimited crew, full alert history, and a MOB waypoint sent to OpenCPN.",
        )
        Text("$appLabel v${versionName ?: "?"} by Trozovka (John Winsome Munar)")
    }
    }
}

/** Teal bar for secondary screens: back arrow + title, same look as the main top bar. */
@Composable
private fun ScreenBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(TealBar).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun DocSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(body)
    }
}

@Composable
private fun SettingsScreen(appLabel: String, versionName: String?, onHowTo: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(modifier = Modifier.fillMaxSize()) {
    ScreenBar("Settings", onClose)
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsRow("How to use", "Step by step, in simple English", onHowTo)
        HorizontalDivider()
        Text("About", fontWeight = FontWeight.Bold)
        Text("$appLabel v${versionName ?: "?"}")
        Text("Man-overboard alarm for two phones. Works offline: no account, no ads, nothing leaves your phone.")
        Text("Made by a Master Mariner for seafarers. Developer: John Winsome Munar (Trozovka).")
        Text("An extra help only. It does not replace your lifejacket, PLB/AIS-MOB beacon or your ship's man-overboard procedure.")
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp)
    }
}
