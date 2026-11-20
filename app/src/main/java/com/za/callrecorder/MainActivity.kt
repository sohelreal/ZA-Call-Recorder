package com.za.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
    private var mediaPlayer: MediaPlayer? = null
    private var isVoiceMemoRecording = false

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

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        listOf("ALL", "INCALL", "OUTCALL", "FAVORITES", "EDITED").forEach {
            tabLayout.addTab(tabLayout.newTab().setText(it))
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { loadRecordings() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) { loadRecordings() }
        })

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_toggle_recording -> toggleAutoRecording()
                R.id.nav_accessibility -> openAccessibilitySettings()
                R.id.nav_speakerphone -> toggleAutoSpeakerphone()
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

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
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
        tvStatus.text = "Auto: ${if (enabled) "ON" else "OFF"} | Both-Sides Acc: $acc | Speaker: ${if (speaker) "ON" else "OFF"}"
    }

    private fun loadRecordings() {
        val dir = File(getExternalFilesDir(null), "Recordings")
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        rvRecordings.adapter = RecordingAdapter(files)
    }

    private fun playFile(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
        Toast.makeText(this, "Playing ${file.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
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
            holder.tvName.text = file.name
            holder.tvMeta.text = "$dateStr  •  ${sizeKb} KB"
            holder.btnPlay.setOnClickListener { playFile(file) }
            holder.btnShare.setOnClickListener { ShareHelper.shareRecording(this@MainActivity, file) }
            holder.btnDelete.setOnClickListener {
                file.delete()
                loadRecordings()
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
