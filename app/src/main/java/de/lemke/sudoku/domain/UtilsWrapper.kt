package de.lemke.sudoku.domain

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import de.lemke.commonutils.deleteAppDataAndExit
import de.lemke.commonutils.openAppLocaleSettings
import de.lemke.commonutils.openURL
import de.lemke.sudoku.R

fun Fragment.openURL(url: String) =
    requireContext().openURL(url, getString(R.string.error_cant_open_url), getString(R.string.no_browser_app_installed))

fun Context.openURL(url: String) =
    openURL(url, getString(R.string.error_cant_open_url), getString(R.string.no_browser_app_installed))

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Fragment.openAppLocaleSettings() = openAppLocaleSettings(getString(R.string.change_language_not_supported_by_device))

fun Fragment.deleteAppDataAndExit(): Boolean = deleteAppDataAndExit(
    title = getString(R.string.delete_appdata_and_exit),
    message = getString(R.string.delete_appdata_and_exit_warning),
    cancel = getString(R.string.sesl_cancel)
)

