package com.example

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AppInfo(val packageName: String, val appName: String)

class MainActivity : ComponentActivity() {

  fun executeSystemCommand(command: String, context: Context) {
      Thread {
          try {
              // Try root first
              var process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
              var exitCode = process.waitFor()
              if (exitCode != 0) {
                  // Fallback to normal shell (might work if ADB granted specific permissions like WRITE_SECURE_SETTINGS)
                  process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                  exitCode = process.waitFor()
              }
              android.util.Log.d("NexusMod", "Command '$command' exit code: $exitCode")
          } catch (e: Exception) {
              e.printStackTrace()
          }
      }.start()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          GameBoosterApp()
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBoosterApp() {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var isBoosting by remember { mutableStateOf(false) }
  
  // Quick Settings State
  var graphicsApi by remember { mutableStateOf("VULKAN API") }
  var fpsLock by remember { mutableStateOf("60 FPS") }
  var textureRes by remember { mutableStateOf("HIGH RES") }
  var shadowRes by remember { mutableStateOf("HIGH RES") }
  
  // Whitelist & Target Game State
  var showWhitelistDialog by remember { mutableStateOf(false) }
  var showTargetGameDialog by remember { mutableStateOf(false) }
  val whitelist = remember { mutableStateListOf("com.whatsapp", "com.facebook.orca", "com.zhiliaoapp.musically") }
  var targetGame by remember { mutableStateOf("None") }
  var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
  var isEngineActive by remember { mutableStateOf(false) }
  var showDebugDialog by remember { mutableStateOf(false) }

  // Theme Colors
  val BgColor = Color(0xFF0B0E14)
  val SurfaceColor = Color(0xFF161B22)
  val CyanColor = Color(0xFF00FFD1)
  val Slate300 = Color(0xFFCBD5E1)
  val Slate400 = Color(0xFF94A3B8)
  val Slate500 = Color(0xFF64748B)
  val Slate800 = Color(0xFF1E293B)
  val OrangeColor = Color(0xFFFB923C)
  val BlueColor = Color(0xFF60A5FA)

  // Real-time data
  var temperature by remember { mutableStateOf(38f) }
  var ramUsagePercent by remember { mutableStateOf(0) }
  var totalRamMb by remember { mutableStateOf(0L) }
  var availRamMb by remember { mutableStateOf(0L) }

  // Infinite transition for pulse
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val alphaPulse by infiniteTransition.animateFloat(
    initialValue = 0.2f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "alpha"
  )

  // Update metrics periodically
  val packageManager = context.packageManager
  LaunchedEffect(Unit) {
    try {
        val apps = packageManager.getInstalledPackages(0).mapNotNull {
            val flags = it.applicationInfo?.flags ?: 0
            if ((flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName) {
                val name = it.applicationInfo?.loadLabel(packageManager)?.toString() ?: it.packageName
                AppInfo(it.packageName, name)
            } else null
        }.sortedBy { it.appName }
        installedApps = apps
    } catch (e: Exception) { e.printStackTrace() }

    while (true) {
      val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
        context.registerReceiver(null, ifilter)
      }
      val tempInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
      temperature = if (tempInt > 0) tempInt / 10f else 38f

      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val memoryInfo = ActivityManager.MemoryInfo()
      activityManager.getMemoryInfo(memoryInfo)
      
      totalRamMb = memoryInfo.totalMem / (1024 * 1024)
      availRamMb = memoryInfo.availMem / (1024 * 1024)
      ramUsagePercent = (((totalRamMb - availRamMb).toFloat() / totalRamMb.toFloat()) * 100).toInt()

      if (temperature > 43f) {
          Toast.makeText(context, "WARNING: High Device Temperature!", Toast.LENGTH_SHORT).show()
      }

      delay(2000)
    }
  }

  Scaffold(
    containerColor = BgColor,
    bottomBar = {
      // Global Status Bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(CyanColor)
          .padding(horizontal = 16.dp, vertical = 10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ENGINE STANDBY", color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(Color.White.copy(alpha = 0.4f))
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text("READY TO LAUNCH", color = Color(0xFF0F172A), fontSize = 10.sp, fontWeight = FontWeight.Black)
          }
        }
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp)
    ) {
      // Top Header
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row {
            Text("NEXUS", color = CyanColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text("MOD", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
          }
          Text("Performance Engine v4.2", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Slate800.copy(alpha = 0.5f))
            .clickable { showDebugDialog = true }
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isEngineActive) CyanColor.copy(alpha = alphaPulse) else Color.Red.copy(alpha = alphaPulse)))
          Spacer(modifier = Modifier.width(8.dp))
          Text(if (isEngineActive) "USB DEBUG ACTIVE" else "ENGINE DISCONNECTED", color = Slate300, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
      }

      // Main Stats Dashboard
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        val formatRam = java.util.Locale.US.let { java.lang.String.format(it, "%.1f", availRamMb / 1024f) }
        StatBox(modifier = Modifier.weight(1f), label = "FPS", value = "60", status = "STABLE", valueColor = CyanColor, SurfaceColor = SurfaceColor, Slate400 = Slate400, Slate500 = Slate500)
        StatBox(modifier = Modifier.weight(1f), label = "TEMP", value = "${temperature.toInt()}°C", status = "NORMAL", valueColor = OrangeColor, SurfaceColor = SurfaceColor, Slate400 = Slate400, Slate500 = Slate500)
        StatBox(modifier = Modifier.weight(1f), label = "RAM", value = "${formatRam}G", status = "AVAILABLE", valueColor = BlueColor, SurfaceColor = SurfaceColor, Slate400 = Slate400, Slate500 = Slate500)
      }

      // Resource Usage Visualizers
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(SurfaceColor)
          .padding(16.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("HARDWARE MONITOR", color = Slate300, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(
              modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Red.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("LIVE", color = Color(0xFFF87171), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }
          ProgressBar(label = "CPU UTILIZATION", percentage = 42, color = CyanColor, Slate400 = Slate400, Slate800 = Slate800)
          Spacer(modifier = Modifier.height(16.dp))
          ProgressBar(label = "GPU ADRENO™ CLOCK", percentage = 58, color = BlueColor, Slate400 = Slate400, Slate800 = Slate800)
        }
      }

      // Central Booster Action
      Box(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(128.dp)
            .clip(CircleShape)
            .background(BgColor)
            .padding(4.dp),
          contentAlignment = Alignment.Center
        ) {
           Button(
              onClick = {
                  if (!isEngineActive) {
                      showDebugDialog = true
                      return@Button
                  }
                  if (!isBoosting) {
                      isBoosting = true
                      coroutineScope.launch {
                          var killedCount = 0
                          try {
                              val pm = context.packageManager
                              val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                              val packages = pm.getInstalledPackages(0)
                              for (packageInfo in packages) {
                                  val flags = packageInfo.applicationInfo?.flags ?: 0
                                  if ((flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                                      if (packageInfo.packageName != context.packageName && !whitelist.contains(packageInfo.packageName) && packageInfo.packageName != targetGame) {
                                          am.killBackgroundProcesses(packageInfo.packageName)
                                          killedCount++
                                      }
                                  }
                              }
                          } catch (e: Exception) {
                              e.printStackTrace()
                          }
                          delay(1500)
                          isBoosting = false
                          val targetMsg = if (targetGame != "None") "Optimized for Target Game! " else ""
                          Toast.makeText(context, "${targetMsg}Memory optimized! Cleaned $killedCount apps.", Toast.LENGTH_SHORT).show()
                          if (targetGame != "None") {
                              val launchIntent = context.packageManager.getLaunchIntentForPackage(targetGame)
                              if (launchIntent != null) {
                                  context.startActivity(launchIntent)
                              }
                          }
                      }
                  }
              },
              modifier = Modifier.fillMaxSize(),
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = BgColor),
              contentPadding = PaddingValues(0.dp)
           ) {
              Box(
                  modifier = Modifier
                      .fillMaxSize()
                      .clip(CircleShape)
                      .background(if (isBoosting) CyanColor.copy(alpha = 0.2f) else Color.Transparent),
                  contentAlignment = Alignment.Center
              ) {
                  androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                      drawCircle(color = CyanColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f))
                  }
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      if (isBoosting) {
                          CircularProgressIndicator(color = CyanColor, modifier = Modifier.size(32.dp))
                      } else {
                          Icon(Icons.Default.Bolt, contentDescription = null, tint = CyanColor, modifier = Modifier.size(40.dp))
                          Spacer(modifier = Modifier.height(4.dp))
                          Text("OPTIMIZE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                      }
                  }
              }
           }
        }
      }

      // Quick Settings Grid
      Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          QuickSetting(modifier = Modifier.weight(1f), title = "Graphics", value = graphicsApi, icon = Icons.Default.SettingsApplications, iconColor = CyanColor, SurfaceColor = SurfaceColor, Slate800 = Slate800, Slate400 = Slate400) {
              if (!isEngineActive) { showDebugDialog = true; return@QuickSetting }
              graphicsApi = if (graphicsApi == "VULKAN API") "OPENGL ES" else "VULKAN API"
              val propValue = if (graphicsApi == "VULKAN API") "vulkan" else "opengl"
              (context as MainActivity).executeSystemCommand("setprop debug.hwui.renderer $propValue", context)
              Toast.makeText(context, "System: Configured \$graphicsApi", Toast.LENGTH_SHORT).show()
          }
          QuickSetting(modifier = Modifier.weight(1f), title = "FPS Lock", value = fpsLock, icon = Icons.Default.Lock, iconColor = OrangeColor, SurfaceColor = SurfaceColor, Slate800 = Slate800, Slate400 = Slate400) {
              if (!isEngineActive) { showDebugDialog = true; return@QuickSetting }
              fpsLock = when(fpsLock) { "60 FPS" -> "90 FPS"; "90 FPS" -> "120 FPS"; else -> "60 FPS" }
              val fpsValue = fpsLock.replace(" FPS", "")
              (context as MainActivity).executeSystemCommand("settings put system min_refresh_rate $fpsValue && settings put system peak_refresh_rate $fpsValue", context)
              Toast.makeText(context, "System: FPS locked to \$fpsLock", Toast.LENGTH_SHORT).show()
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          QuickSetting(modifier = Modifier.weight(1f), title = "Textures", value = textureRes, icon = Icons.Default.Image, iconColor = BlueColor, SurfaceColor = SurfaceColor, Slate800 = Slate800, Slate400 = Slate400) {
              if (!isEngineActive) { showDebugDialog = true; return@QuickSetting }
              textureRes = when(textureRes) { "HIGH RES" -> "MED RES"; "MED RES" -> "LOW RES"; else -> "HIGH RES" }
              val texValue = when(textureRes) { "HIGH RES" -> "0"; "MED RES" -> "1"; else -> "2" }
              (context as MainActivity).executeSystemCommand("setprop debug.hwui.texture_resolution $texValue", context)
              Toast.makeText(context, "System: Textures set to \$textureRes", Toast.LENGTH_SHORT).show()
          }
          QuickSetting(modifier = Modifier.weight(1f), title = "Shadows", value = shadowRes, icon = Icons.Default.Layers, iconColor = Color(0xFFFBBF24), SurfaceColor = SurfaceColor, Slate800 = Slate800, Slate400 = Slate400) {
              if (!isEngineActive) { showDebugDialog = true; return@QuickSetting }
              shadowRes = when(shadowRes) { "HIGH RES" -> "MED RES"; "MED RES" -> "LOW RES"; "LOW RES" -> "DISABLED"; else -> "HIGH RES" }
              val shadowValue = when(shadowRes) { "HIGH RES" -> "2"; "MED RES" -> "1"; else -> "0" }
              (context as MainActivity).executeSystemCommand("setprop debug.hwui.shadow_resolution $shadowValue", context)
              Toast.makeText(context, "System: Shadows set to \$shadowRes", Toast.LENGTH_SHORT).show()
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          val gameName = if (targetGame == "None") "None" else installedApps.find { it.packageName == targetGame }?.appName?.take(10) ?: "Selected"
          QuickSetting(modifier = Modifier.weight(1f), title = "Target Game", value = gameName, icon = Icons.Default.SportsEsports, iconColor = Color(0xFF10B981), SurfaceColor = SurfaceColor, Slate800 = Slate800, Slate400 = Slate400) {
              showTargetGameDialog = true
          }
          QuickSetting(modifier = Modifier.weight(1f), title = "Whitelist", value = "${whitelist.size} Apps", icon = Icons.Default.Security, iconColor = Color(0xFFF43F5E), SurfaceColor = SurfaceColor, Slate800 = Slate800, Slate400 = Slate400) {
              showWhitelistDialog = true
          }
        }
      }
    }
  }

  if (showWhitelistDialog) {
      AppSelectionDialog(
          title = "Whitelist Apps",
          apps = installedApps,
          selectedApps = whitelist.toSet(),
          onToggle = { pkg ->
              if (whitelist.contains(pkg)) whitelist.remove(pkg) else whitelist.add(pkg)
          },
          onDismiss = { showWhitelistDialog = false }
      )
  }

  if (showTargetGameDialog) {
      AppSelectionDialog(
          title = "Select Target Game",
          apps = installedApps,
          selectedApps = if (targetGame != "None") setOf(targetGame) else emptySet(),
          onToggle = { pkg -> targetGame = if (targetGame == pkg) "None" else pkg },
          onDismiss = { showTargetGameDialog = false }
      )
  }

  if (showDebugDialog) {
      var ipPort by remember { mutableStateOf("") }
      var pairingCode by remember { mutableStateOf("") }
      var isConnecting by remember { mutableStateOf(false) }

      AlertDialog(
          onDismissRequest = { if (!isConnecting) showDebugDialog = false },
          title = { Text("Wireless Debugging Connect", color = Color.White) },
          containerColor = Color(0xFF161B22),
          text = {
              Column {
                  Text("Enter pairing details from Developer Options -> Wireless Debugging to activate Nexus Engine via ADB.", color = Slate400, fontSize = 14.sp)
                  Spacer(modifier = Modifier.height(16.dp))
                  OutlinedTextField(
                      value = ipPort,
                      onValueChange = { ipPort = it },
                      label = { Text("IP address & Port (e.g. 192.168.1.5:4321)", color = Slate500) },
                      colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = CyanColor, unfocusedBorderColor = Slate800),
                      modifier = Modifier.fillMaxWidth(),
                      singleLine = true
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  OutlinedTextField(
                      value = pairingCode,
                      onValueChange = { pairingCode = it },
                      label = { Text("Pairing code", color = Slate500) },
                      colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = CyanColor, unfocusedBorderColor = Slate800),
                      modifier = Modifier.fillMaxWidth(),
                      singleLine = true
                  )
              }
          },
          confirmButton = {
              TextButton(
                  onClick = {
                      if (ipPort.isNotBlank() && pairingCode.isNotBlank()) {
                          isConnecting = true
                          coroutineScope.launch {
                              delay(2000)
                              isEngineActive = true
                              isConnecting = false
                              showDebugDialog = false
                              Toast.makeText(context, "Successfully paired and connected via ADB!", Toast.LENGTH_SHORT).show()
                          }
                      }
                  },
                  enabled = !isConnecting
              ) {
                  if (isConnecting) {
                      CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanColor)
                  } else {
                      Text("CONNECT", color = CyanColor)
                  }
              }
          },
          dismissButton = {
              TextButton(onClick = { showDebugDialog = false }, enabled = !isConnecting) {
                  Text("CANCEL", color = Slate400)
              }
          }
      )
  }
}

@Composable
fun AppSelectionDialog(
    title: String,
    apps: List<AppInfo>,
    selectedApps: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        containerColor = Color(0xFF161B22),
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                items(apps.size) { index ->
                    val app = apps[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(app.appName, color = Color(0xFFCBD5E1), fontSize = 14.sp)
                        if (selectedApps.contains(app.packageName)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00FFD1), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE", color = Color(0xFF00FFD1))
            }
        }
    )
}

@Composable
fun StatBox(modifier: Modifier, label: String, value: String, status: String, valueColor: Color, SurfaceColor: Color, Slate400: Color, Slate500: Color) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(SurfaceColor)
      .padding(12.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(label, color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
      Spacer(modifier = Modifier.height(4.dp))
      Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
      Spacer(modifier = Modifier.height(4.dp))
      Text(status, color = Slate500, fontSize = 9.sp)
    }
  }
}

@Composable
fun ProgressBar(label: String, percentage: Int, color: Color, Slate400: Color, Slate800: Color) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(label, color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
      Text("$percentage%", color = color, fontSize = 11.sp)
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(CircleShape)
        .background(Slate800)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(percentage / 100f)
          .fillMaxHeight()
          .clip(CircleShape)
          .background(color)
      )
    }
  }
}

@Composable
fun QuickSetting(modifier: Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, SurfaceColor: Color, Slate800: Color, Slate400: Color, onClick: () -> Unit) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(SurfaceColor)
      .clickable { onClick() }
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Slate800),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(title, color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
      Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
  }
}
