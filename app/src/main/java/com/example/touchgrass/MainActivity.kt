package com.example.touchgrass

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.app.AppOpsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.random.Random

// --- MUSEUM PALETTE ---
val Parchment = Color(0xFFF4F1E8)
val DeepMoss = Color(0xFF2E3B23)
val SoftGold = Color(0xFFC5A059)
val InkBlack = Color(0xFF1A1A1A)
val FailureRed = Color(0xFF7A2020)
val PolaroidWhite = Color(0xFFFBFBF9)
val VarnishOverlay = Color(0x1A000000)

data class ScrapbookImage(val bitmap: Bitmap, val file: File, val isNature: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TouchGrassMasterApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchGrassMasterApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("touch_grass_prefs", Context.MODE_PRIVATE) }

    // FIX: Remember which screen we were on so we don't reset to Tracker after a photo
    val savedScreen = prefs.getString("last_screen", "tracker") ?: "tracker"
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var totalMinutes by remember { mutableLongStateOf(prefs.getLong("saved_minutes", 0L)) }
    val scrapbook = remember { mutableStateListOf<ScrapbookImage>() }

    // Use v3 to ensure a fresh preference check
    var showAdvice by remember { mutableStateOf(prefs.getBoolean("has_seen_warning_v3", true)) }

    LaunchedEffect(Unit) {
        listOf("nature", "failure").forEach { folder ->
            val dir = File(context.filesDir, folder)
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) scrapbook.add(ScrapbookImage(bitmap, file, folder == "nature"))
                }
            }
        }
    }

    // Update preferences whenever the destination changes
    navController.addOnDestinationChangedListener { _, destination, _ ->
        prefs.edit().putString("last_screen", destination.route).apply()
    }

    if (showAdvice) {
        AlertDialog(
            onDismissRequest = {
                showAdvice = false
                prefs.edit().putBoolean("has_seen_warning_v3", false).apply()
            },
            containerColor = Parchment,
            title = { Text("A WARNING, IF YOU CHOOSE TO ACCEPT IT...", fontWeight = FontWeight.Bold, color = DeepMoss) },
            text = {
                Text(
                    "Welcome, digital drifter. Your garden is dying. To unlock your other distractions, you must provide 'Proof of Life'—a photo of actual nature. \n\n1. Use THE JUDGMENT to see your rot.\n2. Submit nature to stay sane.\n3. Fail, and you shall be heard.",
                    fontFamily = FontFamily.Serif, color = InkBlack
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAdvice = false
                    prefs.edit().putBoolean("has_seen_warning_v3", false).apply()
                }) {
                    Text("I ACCEPT MY FATE", color = SoftGold, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Parchment)) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Parchment) {
                    Text("NAVIGATION", modifier = Modifier.padding(24.dp), color = SoftGold, fontWeight = FontWeight.Bold)
                    NavigationDrawerItem(label = { Text("THE JUDGMENT") }, selected = false, onClick = { navController.navigate("tracker"); scope.launch { drawerState.close() } })
                    NavigationDrawerItem(label = { Text("THE SCRAPBOOK") }, selected = false, onClick = { navController.navigate("gallery"); scope.launch { drawerState.close() } })
                    NavigationDrawerItem(label = { Text("THE TRUTH") }, selected = false, onClick = { navController.navigate("about"); scope.launch { drawerState.close() } })
                }
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("TOUCH GRASS", color = DeepMoss, fontWeight = FontWeight.Black) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = null, tint = DeepMoss)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                },
                containerColor = Color.Transparent
            ) { padding ->
                // START DESTINATION is now loaded from saved preferences
                NavHost(navController = navController, startDestination = savedScreen, modifier = Modifier.padding(padding)) {
                    composable("tracker") {
                        TrackerScreen(context, totalMinutes, scrapbook) { newMinutes ->
                            totalMinutes = newMinutes
                            prefs.edit().putLong("saved_minutes", newMinutes).apply()
                        }
                    }
                    composable("gallery") { GalleryScreen(scrapbook) }
                    composable("about") { AboutScreen() }
                }
            }
        }
    }
}
@Composable
fun TrackerScreen(context: Context, minutes: Long, scrapbook: MutableList<ScrapbookImage>, onUpdate: (Long) -> Unit) {
    val prefs = remember { context.getSharedPreferences("touch_grass_prefs", Context.MODE_PRIVATE) }

    val natureBlessings = listOf(
        "Nature detected. You are safe... for now.",
        "Look at you, being an outdoor person. I'm almost proud.",
        "A blade of grass! Your soul is healing.",
        "Sunlight confirmed. You've escaped the digital rot.",
        "The Curator is pleased. You may live another day."
    )

    val rotRoasts = listOf(
        "Faaaaahh! Are your brain cells still alive?",
        "Do you even know what a tree looks like anymore?",
        "Pathetic. That's a monitor, not a meadow.",
        "Blue light is not a personality trait. Try again.",
        "The garden is dying, and you're staring at pixels. Shame."
    )

    // FIX 2: Use rememberSaveable so the roast text doesn't disappear when the camera launches
    var roast by rememberSaveable { mutableStateOf("Ready to be humbled?") }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { b ->
                verifyWithAI(b) { isNature ->
                    val folder = if (isNature) "nature" else "failure"
                    val file = saveBitmapToInternal(context, b, folder)
                    scrapbook.add(ScrapbookImage(b, file, isNature))

                    if (!isNature) {
                        playFailSound(context)
                        roast = rotRoasts.random()
                        prefs.edit().putBoolean("is_locked", true).apply()
                    } else {
                        roast = natureBlessings.random()
                        prefs.edit().putBoolean("is_locked", false).apply()
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WitheredGardenPainter(minutes, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp).border(2.dp, SoftGold, CircleShape).padding(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${minutes}m", fontSize = 90.sp, color = InkBlack, fontFamily = FontFamily.Serif)
                    Text("OF DIGITAL ROT", fontSize = 12.sp, color = SoftGold, letterSpacing = 5.sp)
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            Text(roast, textAlign = TextAlign.Center, color = InkBlack, fontStyle = FontStyle.Italic, fontSize = 18.sp, fontFamily = FontFamily.Serif)
            Spacer(modifier = Modifier.height(60.dp))

            Button(onClick = {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraLauncher.launch(intent)
            }, colors = ButtonDefaults.buttonColors(containerColor = DeepMoss), modifier = Modifier.fillMaxWidth().height(55.dp)) {
                Text("SUBMIT PROOF OF LIFE", color = Parchment)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = {
                if (hasUsageStatsPermission(context)) {
                    onUpdate(getTodayScreenTime(context) / 60000)
                } else {
                    Toast.makeText(context, "Please enable Usage Access to see your rot.", Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("REFRESH MY SHAME", color = DeepMoss)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // RESTORED: Accessibility Button
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) {
                Text("ACTIVATE THE GUARDIAN (LOCK APPS)", color = SoftGold, fontSize = 11.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            }
        }
    }
}

@Composable
fun AboutScreen() {
    Box(modifier = Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
        // --- AESTHETIC MUSEUM PLAQUE ---
        Box(modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .border(4.dp, SoftGold)
            .padding(4.dp)
            .border(1.dp, SoftGold.copy(alpha = 0.5f))
            .background(PolaroidWhite)
            .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CURATOR'S NOTE", color = SoftGold, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "This app was curated for my boyfriend—a majestic, certified nerd who occasionally forgets that the sun is a real celestial body and not just a high-res light source. I built this to ensure he actually perceives a blade of grass once in a while. You're welcome.",
                    textAlign = TextAlign.Center, color = InkBlack, fontFamily = FontFamily.Serif,
                    fontSize = 14.sp, // Made smaller as requested
                    fontStyle = FontStyle.Italic,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(30.dp))
                Divider(color = SoftGold.copy(alpha = 0.3f), modifier = Modifier.width(40.dp))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "TOUGH LOVE • EST. 2026 • ADDIS ABABA",
                    color = DeepMoss.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GalleryScreen(scrapbook: MutableList<ScrapbookImage>) {
    val naturePics = scrapbook.filter { it.isNature }
    val failurePics = scrapbook.filter { !it.isNature }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (naturePics.isNotEmpty()) {
            SectionHeader("EXHIBIT A: PROOF OF LIFE", DeepMoss)
            PhotoExhibit(naturePics, true) { scrapbook.remove(it) }
        }
        Spacer(modifier = Modifier.height(40.dp))
        if (failurePics.isNotEmpty()) {
            SectionHeader("EXHIBIT B: WALL OF SHAME", FailureRed)
            PhotoExhibit(failurePics, false) { scrapbook.remove(it) }
        }
    }
}

@Composable
fun SectionHeader(title: String, color: Color) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Divider(color = SoftGold.copy(alpha = 0.4f))
    }
}

@Composable
fun PhotoExhibit(images: List<ScrapbookImage>, isNature: Boolean, onDelete: (ScrapbookImage) -> Unit) {
    Column {
        for (i in images.indices step 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                ArtisticPhotoItem(images[i], if(isNature) -3f else 6f, if(isNature) DeepMoss else FailureRed) { onDelete(images[i]) }
                if (i + 1 < images.size) {
                    ArtisticPhotoItem(images[i+1], if(isNature) 4f else -5f, if(isNature) SoftGold else FailureRed) { onDelete(images[i+1]) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ArtisticPhotoItem(item: ScrapbookImage, baseRot: Float, border: Color, onDelete: () -> Unit) {
    var showDelete by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(155.dp).rotate(baseRot).shadow(10.dp).background(PolaroidWhite).padding(10.dp).pointerInput(Unit) { detectTapGestures(onLongPress = { showDelete = true }) }) {
        Image(bitmap = item.bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
        Text(if(item.isNature) "Organic" else "Rot", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    if (showDelete) {
        AlertDialog(onDismissRequest = { showDelete = false }, confirmButton = { TextButton(onClick = { item.file.delete(); onDelete(); showDelete = false }) { Text("ERASE") } }, title = { Text("Delete?") })
    }
}

fun verifyWithAI(bitmap: Bitmap, onResult: (Boolean) -> Unit) {
    val options = ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build()
    val labeler = ImageLabeling.getClient(options)
    val image = InputImage.fromBitmap(bitmap, 0)

    labeler.process(image)
        .addOnSuccessListener { labels ->
            val tags = labels.map { it.text.lowercase() }
            val natureKeywords = listOf("grass", "plant", "tree", "leaf", "flower", "forest", "vegetation", "garden", "outdoor", "wood", "flora")
            val techKeywords = listOf("monitor", "screen", "computer", "electronics", "laptop", "tv", "cell phone", "display", "gadget", "keyboard", "computer monitor", "television", "desktop computer", "personal computer", "output device", "computer keyboard")

            val hasNature = tags.any { it in natureKeywords }
            val hasTech = tags.any { it in techKeywords }
            onResult(hasNature && !hasTech)
        }
        .addOnFailureListener { onResult(false) }
}

@Composable
fun WitheredGardenPainter(totalMinutes: Long, modifier: Modifier) {
    Canvas(modifier = modifier.height(180.dp)) {
        val health = (1.0f - (totalMinutes / 300f)).coerceIn(0.1f, 1.0f)
        for (i in 0..25) {
            val x = (size.width / 25) * i
            val h = (70..160).random().toFloat() * health
            drawPath(Path().apply { moveTo(x, size.height); quadraticTo(x + 15, size.height - h, x + 30, size.height) }, color = if (health < 0.4) Color(0xFF6B5335) else DeepMoss.copy(alpha = 0.5f))
        }
    }
}

fun getTodayScreenTime(c: Context): Long {
    val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
    return stats?.sumOf { it.totalTimeInForeground } ?: 0L
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun playFailSound(context: Context) {
    try {
        val mp = MediaPlayer.create(context, R.raw.faaaah)
        mp.setOnCompletionListener { it.release() }
        mp.start()
    } catch (e: Exception) {}
}

fun saveBitmapToInternal(context: Context, bitmap: Bitmap, folder: String): File {
    val directory = File(context.filesDir, folder)
    if (!directory.exists()) directory.mkdirs()
    val file = File(directory, "img_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    return file
}