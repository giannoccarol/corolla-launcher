package io.github.giannoccarol.corollalauncher.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat

/**
 * Picking and calling favourites. The contact picker returns only the row the driver chose and
 * grants access to it alone, so the launcher never needs to read the whole address book.
 */
object Calls {

    /** The system contact picker, limited to phone numbers. */
    fun pickIntent(): Intent = Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    /** The name and number of the row returned by [pickIntent], or null if unreadable. */
    fun readPicked(context: Context, uri: Uri): Favorite? = try {
        context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val name = cursor.getString(0).orEmpty()
                val number = cursor.getString(1).orEmpty()
                Favorites.decode(Favorites.encode(Favorite(name.ifBlank { number }, number)))
            }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    fun canCallDirectly(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Calls [number] when [direct] (the call permission is granted), otherwise opens the dialer
     * with the number ready. False when the unit has no app for either.
     */
    fun call(context: Context, number: String, direct: Boolean): Boolean {
        val uri = Uri.fromParts("tel", number, null)
        if (direct) {
            try {
                context.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                // Fall through to the dialer.
            } catch (e: SecurityException) {
                // Permission revoked meanwhile: the dialer needs none.
            }
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
