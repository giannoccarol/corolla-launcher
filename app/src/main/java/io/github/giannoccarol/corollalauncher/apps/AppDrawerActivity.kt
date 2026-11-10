package io.github.giannoccarol.corollalauncher.apps

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.databinding.ActivityAppDrawerBinding
import io.github.giannoccarol.corollalauncher.ui.SystemBars

/** Every installed app in a searchable grid. */
class AppDrawerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var adapter: AppGridAdapter
    private val appsListener: () -> Unit = { applyFilter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this, binding.root, includeIme = true)

        adapter = AppGridAdapter(
            onClick = ::launch,
            onLongClick = { anchor, app -> AppActions.showMenu(anchor, app) {} },
        )
        val columns = (resources.configuration.screenWidthDp / TILE_WIDTH_DP).coerceIn(3, 8)
        binding.grid.layoutManager = GridLayoutManager(this, columns)
        binding.grid.adapter = adapter

        binding.backButton.setOnClickListener { finish() }
        binding.search.doAfterTextChanged { applyFilter() }

        AppRepository.addListener(appsListener)
        applyFilter()
    }

    override fun onDestroy() {
        AppRepository.removeListener(appsListener)
        super.onDestroy()
    }

    private fun applyFilter() {
        val query = binding.search.text.toString().trim()
        val apps = AppRepository.apps.filter {
            query.isEmpty() || it.label.contains(query, ignoreCase = true)
        }
        adapter.submit(apps)
        binding.empty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launch(app: AppInfo) {
        if (AppRepository.launch(this, app)) {
            finish()
        } else {
            Toast.makeText(
                this, getString(R.string.app_launch_failed, app.label), Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private companion object {
        const val TILE_WIDTH_DP = 150
    }
}
