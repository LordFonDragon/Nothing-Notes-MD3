package com.maltaisn.notes.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.color.DynamicColors
import com.maltaisn.notes.App
import com.maltaisn.notes.NavGraphMainDirections
import com.maltaisn.notes.R
import com.maltaisn.notes.TAG
import com.maltaisn.notes.databinding.ActivityMainBinding
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.converter.NoteTypeConverter
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.receiver.AlarmReceiver
import com.maltaisn.notes.ui.SharedViewModel
import com.maltaisn.notes.ui.main.MainViewModel.NewNoteData
import com.maltaisn.notes.ui.navGraphViewModel
import com.maltaisn.notes.ui.navigation.HomeDestination
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.viewModel
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Provider

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) { sharedViewModelProvider.get() }

    @Inject
    lateinit var viewModelFactory: MainViewModel.Factory
    private val viewModel by viewModel { viewModelFactory.create(it) }

    @Inject
    lateinit var prefs: PrefsManager

    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)

        super.onCreate(savedInstanceState)
        (applicationContext as App).appComponent.inject(this)

        // Apply dynamic colors
        if (prefs.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Allow for transparent status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply padding to navigation drawer
        val initialPadding = resources.getDimensionPixelSize(R.dimen.navigation_drawer_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { _, insets ->
            val sysWindow = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navView.getHeaderView(0).updatePadding(top = sysWindow.top)
            binding.navView.children.last().updatePadding(bottom = initialPadding + sysWindow.bottom)
            // Don't draw under system bars, if it conflicts with the navigation drawer.
            if (sysWindow.left > 0) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            insets
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this)

        binding.navView.setNavigationItemSelectedListener { item ->
            viewModel.navigationItemSelected(
                item,
                binding.navView.menu.findItem(R.id.drawer_labels).subMenu!!
            )
            true
        }
        viewModel.startPopulatingDrawerWithLabels()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawers()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setupViewModelObservers()
    }

    private fun setupViewModelObservers() {
        val menu = binding.navView.menu
        val labelSubmenu = menu.findItem(R.id.drawer_labels).subMenu!!
        var currentHomeDestination: HomeDestination = HomeDestination.Status(NoteStatus.ACTIVE)

        viewModel.currentHomeDestination.observe(this) { newHomeDestination ->
            sharedViewModel.changeHomeDestination(newHomeDestination)
            currentHomeDestination = newHomeDestination
        }

        viewModel.navDirectionsEvent.observeEvent(this) { navDirections ->
            navController.navigateSafe(navDirections)
        }

        viewModel.drawerCloseEvent.observeEvent(this) {
            binding.drawerLayout.closeDrawers()
        }

        viewModel.clearLabelsEvent.observeEvent(this) {
            labelSubmenu.clear()
        }

        viewModel.labelsAddEvent.observeEvent(this) { labels ->
            if (labels != null) {
                for (label in labels) {
                    labelSubmenu.add(Menu.NONE, View.generateViewId(), Menu.NONE, label.name)
                        .setIcon(R.drawable.ic_label_outline).isCheckable = true
                }
            }

            if (currentHomeDestination is HomeDestination.Labels) {
                val currentLabelName = (currentHomeDestination as HomeDestination.Labels).label.name
                if (binding.navView.checkedItem != null && (
                            binding.navView.checkedItem !in labelSubmenu ||
                                    binding.navView.checkedItem!!.title != currentLabelName)
                    || binding.navView.checkedItem == null
                ) {
                    labelSubmenu.forEach { item: MenuItem ->
                        if (item.title == currentLabelName) {
                            binding.navView.setCheckedItem(item)
                            return@forEach
                        }
                    }
                }
            }
        }

        viewModel.manageLabelsVisibility.observe(this) { isVisible ->
            menu.findItem(R.id.drawer_item_edit_labels).isVisible = isVisible
        }

        viewModel.editItemEvent.observeEvent(this) { noteId ->
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(noteId), true)
        }

        viewModel.autoExportEvent.observeEvent(this) { uri ->
            viewModel.autoExport(try {
                contentResolver.openOutputStream(Uri.parse(uri), "wt")
            } catch (e: Exception) {
                Log.i(TAG, "Auto data export failed", e)
                null
            })
        }

        viewModel.createNoteEvent.observeEvent(this) { newNoteData ->
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(
                type = newNoteData.type.value,
                title = newNoteData.title,
                content = newNoteData.content
            ))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        binding.drawerLayout.setDrawerLockMode(if (destination.id == R.id.fragment_home) {
            DrawerLayout.LOCK_MODE_UNLOCKED
        } else {
            DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        })
    }

    override fun onStart() {
        super.onStart()

        sharedViewModel.labelAddEventNav.observeEvent(this) { label ->
            if (navController.previousBackStackEntry?.destination?.id == R.id.fragment_home) {
                viewModel.selectLabel(label)
            }
        }

        viewModel.onStart()
    }

    override fun onResume() {
        super.onResume()
        handleIntent()
    }

    override fun onDestroy() {
        super.onDestroy()
        navController.removeOnDestinationChangedListener(this)
    }

    private fun handleIntent() {
        val intent = intent ?: return
        if (!intent.getBooleanExtra(KEY_INTENT_HANDLED, false)) {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val noteData = createNoteFromIntent(intent)
                    if (noteData != null) {
                        viewModel.createNote(noteData)
                    }
                }
                INTENT_ACTION_CREATE -> {
                    val type = NoteTypeConverter.toType(
                        intent.getIntExtra(EXTRA_NOTE_TYPE, 0))
                    viewModel.createNote(NewNoteData(type))
                }
                INTENT_ACTION_EDIT -> {
                    viewModel.editNote(intent.getLongExtra(AlarmReceiver.EXTRA_NOTE_ID, Note.NO_ID))
                }
                INTENT_ACTION_SHOW_REMINDERS -> {
                    binding.navView.menu.findItem(R.id.drawer_item_reminders).isChecked = true
                    sharedViewModel.changeHomeDestination(HomeDestination.Reminders)
                }
            }

            intent.putExtra(KEY_INTENT_HANDLED, true)
        }
    }

    private fun createNoteFromIntent(intent: Intent): NewNoteData? {
        val extras = intent.extras ?: return null
        var noteData: NewNoteData? = null
        if (intent.type == "text/plain") {
            if (extras.containsKey(Intent.EXTRA_STREAM)) {
                @Suppress("DEPRECATION")
                val uri = extras.get(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) {
                    try {
                        val reader = InputStreamReader(contentResolver.openInputStream(uri))
                        val title = uri.pathSegments.last()
                        val content = reader.readText()
                        noteData = NewNoteData(NoteType.TEXT, title, content)
                        reader.close()
                    } catch (e: IOException) {
                        // nothing to do (file doesn't exist, access error, etc)
                    }
                }
            } else {
                val title = extras.getString(Intent.EXTRA_TITLE)
                    ?: extras.getString(Intent.EXTRA_SUBJECT) ?: ""
                val content = extras.getString(Intent.EXTRA_TEXT) ?: ""
                noteData = NewNoteData(NoteType.TEXT, title, content)
            }
        }
        return noteData
    }

    companion object {
        private const val KEY_INTENT_HANDLED = "com.maltaisn.notes.INTENT_HANDLED"

        const val EXTRA_NOTE_TYPE = "com.maltaisn.notes.NOTE_TYPE"

        const val INTENT_ACTION_CREATE = "com.maltaisn.notes.CREATE"
        const val INTENT_ACTION_EDIT = "com.maltaisn.notes.EDIT"
        const val INTENT_ACTION_SHOW_REMINDERS = "com.maltaisn.notes.SHOW_REMINDERS"
    }
}
