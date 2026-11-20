package com.za.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rvRecordings: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var fabVoiceMemo: FloatingActionButton

    // Interactive Audio Player views
    private lateinit var playerCard: MaterialCardView
    private lateinit var tvPlayerTitle: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnSpeed: TextView
    private lateinit var btnPlayerClose: ImageButton

    private var mediaPlayer: MediaPlayer? = null
    private var isUserSeeking = false
    private var playbackSpeed = 1.0f
    private var isVoiceMemoRecording = false
    private var selectedTab = "ALL"

    private val playerHandler = Handler(Looper.getMainLooper())
    private val updateSeekRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying && !isUserSeeking) {
                seekBar.progress = mp.currentPosition
                tvCurrentTime.text = formatTime(mp.currentPosition)
            }
            if (mp != null) {
                playerHandler.postDelayed(this, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, 0, 0)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        tvStatus = findViewById(R.id.tvStatus)
        rvRecordings = findViewById(R.id.rvRecordings)
        rvRecordings.layoutManager = LinearLayoutManager(this)
        fabVoiceMemo = findViewById(R.id.fabVoiceMemo)

        initPlayerViews()

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        listOf("ALL", "INCALL", "OUTCALL", "FAVORITES", "EDITED").forEach {
            tabLayout.addTab(tabLayout.newTab().setText(it))
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTab = tab?.text?.toString() ?: "ALL"
                loadRecordings()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) { loadRecordings() }
        })

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_toggle_recording -> toggleAutoRecording()
                R.id.nav_accessibility -> openAccessibilitySettings()
                R.id.nav_speakerphone -> toggleAutoSpeakerphone()
                R.id.nav_audio_source -> showAudioSourceDialog()
                R.id.nav_audio_format -> showFormatDialog()
                R.id.nav_refresh -> loadRecordings()
            }
            drawerLayout.closeDrawers()
            true
        }

        fabVoiceMemo.setOnClickListener { toggleVoiceMemo() }

        requestRequiredPermissions()
        updateStatusBanner()
        loadRecordings()
    }

    private fun initPlayerViews() {
        playerCard = findViewById(R.id.playerCard)
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnRewind = findViewById(R.id.btnRewind)
        btnForward = findViewById(R.id.btnForward)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnPlayerClose = findViewById(R.id.btnPlayerClose)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserSeeking = true
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
                mediaPlayer?.seekTo(sb?.progress ?: 0)
            }
        })

        btnPlayPause.setOnClickListener {
            val mp = mediaPlayer ?: return@setOnClickListener
            if (mp.isPlaying) {
                mp.pause()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                mp.start()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        btnRewind.setOnClickListener {
            val mp = mediaPlayer ?: return@setOnClickListener
            val target = (mp.currentPosition - 10_000).coerceAtLeast(0)
            mp.seekTo(target)
            seekBar.progress = target
            tvCurrentTime.text = formatTime(target)
        }

        btnForward.setOnClickListener {
            val mp = mediaPlayer ?: return@setOnClickListener
            val target = (mp.currentPosition + 10_000).coerceAtMost(mp.duration)
            mp.seekTo(target)
            seekBar.progress = target
            tvCurrentTime.text = formatTime(target)
        }

        btnSpeed.setOnClickListener {
            playbackSpeed = when (playbackSpeed) {
                1.0f -> 1.5f
                1.5f -> 2.0f
                else -> 1.0f
            }
            btnSpeed.text = "${playbackSpeed}x"
            applyPlaybackSpeed()
        }

        btnPlayerClose.setOnClickListener {
            stopAndClosePlayer()
        }
    }

    private fun applyPlaybackSpeed() {
        val mp = mediaPlayer ?: return
        try {
            val params = mp.playbackParams
            params.speed = playbackSpeed
            mp.playbackParams = params
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAndClosePlayer() {
        playerHandler.removeCallbacks(updateSeekRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        playerCard.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        updateStatusBanner()
        loadRecordings()
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this, "Enable 'ZA Call Recorder' for two-sided recording", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleAutoSpeakerphone() {
        val prefs = getSharedPreferences("za_prefs", MODE_PRIVATE)
        val current = prefs.getBoolean("auto_speakerphone", false)
        prefs.edit().putBoolean("auto_speakerphone", !current).apply()
        updateStatusBanner()
    }

    private fun showAudioSourceDialog() {
        val sources = arrayOf("VOICE_RECOGNITION", "MIC", "VOICE_COMMUNICATION", "VOICE_CALL")
        val prefs = getSharedPreferences("za_prefs", MODE_PRIVATE)
        val current = prefs.getString("audio_source", "VOICE_RECOGNITION")
        val checked = sources.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Audio Source (Use VOICE_RECOGNITION on Pixel)")
            .setSingleChoiceItems(sources, checked) { dialog, which ->
                prefs.edit().putString("audio_source", sources[which]).apply()
                updateStatusBanner()
                dialog.dismiss()
            }
            .show()
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun toggleAutoRecording() {
        val prefs = getSharedPreferences("za_prefs", MODE_PRIVATE)
        val current = prefs.getBoolean("recording_enabled", true)
        prefs.edit().putBoolean("recording_enabled", !current).apply()
        updateStatusBanner()
    }

    private fun showFormatDialog() {
        val formats = arrayOf("AAC", "AMR")
        val prefs = getSharedPreferences("za_prefs", MODE_PRIVATE)
        val current = prefs.getString("audio_format", "AAC")
        val checked = if (current == "AMR") 1 else 0
        AlertDialog.Builder(this)
            .setTitle("Select Audio Format")
            .setSingleChoiceItems(formats, checked) { dialog, which ->
                prefs.edit().putString("audio_format", formats[which]).apply()
                updateStatusBanner()
                dialog.dismiss()
            }
            .show()
    }

    private fun toggleVoiceMemo() {
        if (!isVoiceMemoRecording) {
            val intent = Intent(this, CallRecorderService::class.java).apply {
                action = CallRecorderService.ACTION_START
                putExtra("PHONE_NUMBER", "VoiceMemo")
                putExtra("IS_CALL", false)
            }
            ContextCompat.startForegroundService(this, intent)
            isVoiceMemoRecording = true
            Toast.makeText(this, "Recording Voice Memo...", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(this, CallRecorderService::class.java).apply {
                action = CallRecorderService.ACTION_STOP
                putExtra("IS_CALL", false)
            }
            startService(intent)
            isVoiceMemoRecording = false
            Toast.makeText(this, "Voice Memo Saved", Toast.LENGTH_SHORT).show()
            rvRecordings.postDelayed({ loadRecordings() }, 500)
        }
    }

    private fun updateStatusBanner() {
        val prefs = getSharedPreferences("za_prefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean("recording_enabled", true)
        val speaker = prefs.getBoolean("auto_speakerphone", false)
        val acc = if (CallAccessibilityService.isRunning) "ON" else "OFF"
        tvStatus.text = "Auto: ${if (enabled) "ON" else "OFF"} | Acc: $acc | Speaker: ${if (speaker) "ON" else "OFF"}"
    }

    private fun loadRecordings() {
        val dir = File(getExternalFilesDir(null), "Recordings")
        val allFiles = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        val filtered = when (selectedTab) {
            "INCALL" -> allFiles.filter { it.name.startsWith("IN_") }
            "OUTCALL" -> allFiles.filter { it.name.startsWith("OUT_") }
            else -> allFiles
        }
        rvRecordings.adapter = RecordingAdapter(filtered)
    }

    private fun playFile(file: File, displayTitle: String) {
        stopAndClosePlayer()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    seekBar.progress = duration
                    tvCurrentTime.text = formatTime(duration)
                }
                start()
            }
            mediaPlayer = mp
            tvPlayerTitle.text = displayTitle
            seekBar.max = mp.duration
            seekBar.progress = 0
            tvCurrentTime.text = "00:00"
            tvTotalTime.text = formatTime(mp.duration)
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            btnSpeed.text = "${playbackSpeed}x"
            applyPlaybackSpeed()
            playerCard.visibility = View.VISIBLE
            playerHandler.post(updateSeekRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Unable to play file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAndClosePlayer()
    }

    inner class RecordingAdapter(private val items: List<File>) :
        RecyclerView.Adapter<RecordingAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvFileName)
            val tvMeta: TextView = view.findViewById(R.id.tvFileMeta)
            val btnPlay: ImageButton = view.findViewById(R.id.btnPlay)
            val btnShare: ImageButton = view.findViewById(R.id.btnShare)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = items[position]
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(file.lastModified()))
            val sizeKb = file.length() / 1024
            val title = formatDisplayTitle(file.name)
            holder.tvName.text = title
            holder.tvMeta.text = "$dateStr  •  ${sizeKb} KB"
            holder.itemView.setOnClickListener { playFile(file, title) }
            holder.btnPlay.setOnClickListener { playFile(file, title) }
            holder.btnShare.setOnClickListener { ShareHelper.shareRecording(this@MainActivity, file) }
            holder.btnDelete.setOnClickListener {
                file.delete()
                loadRecordings()
            }
        }

        private fun formatDisplayTitle(rawName: String): String {
            val base = rawName.substringBeforeLast(".")
            return when {
                base.startsWith("IN_") -> "↙ " + base.removePrefix("IN_").replace("_", " ")
                base.startsWith("OUT_") -> "↗ " + base.removePrefix("OUT_").replace("_", " ")
                else -> base.replace("_", " ")
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
