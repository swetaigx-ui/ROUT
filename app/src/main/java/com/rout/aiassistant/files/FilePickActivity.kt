package com.rout.aiassistant.files

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.rout.aiassistant.control.StepResult
import com.rout.aiassistant.control.Verify

/**
 * Hosts the Android system file picker for file.open / file.rename / file.delete. The USER picks the file; rename and delete then show the REAL
 * file name in a dialog and only act after an explicit Yes here. Closing the picker or the dialog cancels with nothing changed.
 */
class FilePickActivity : ComponentActivity() {
    private var finished = false

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        val req = FileBridge.request
        if (res.resultCode != RESULT_OK || uri == null || req == null) return@registerForActivityResult done(StepResult(false, "File chuni nahi gayi, kuch nahi kiya."))
        handle(req, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = FileBridge.request
        if (req == null || FileBridge.pending == null) { finish(); return }
        if (savedInstanceState != null) return  // picker already shown before a recreate
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (req.op == FileOp.OPEN) 0 else Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        try { picker.launch(i) } catch (e: Exception) { done(StepResult(false, "File picker is phone par nahi mila.")) }
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) { null }

    private fun flags(uri: Uri): Int = try {
        contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { if (it.moveToFirst()) it.getInt(0) else 0 } ?: 0
    } catch (e: Exception) { 0 }

    private fun handle(req: FileRequest, uri: Uri) {
        val name = displayName(uri) ?: "(naam nahi mila)"
        when (req.op) {
            FileOp.OPEN -> {
                val v = Intent(Intent.ACTION_VIEW).setDataAndType(uri, contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { startActivity(v); done(StepResult(true, "'$name' kholne ke liye app chuni ja rahi hai.")) }
                catch (e: Exception) { done(StepResult(false, "'$name' kholne wali koi app nahi mili.")) }
            }
            FileOp.RENAME -> {
                FileRules.invalidName(req.newName)?.let { return done(StepResult(false, it)) }
                if (flags(uri) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME == 0) return done(StepResult(false, "'$name' ko ye storage rename karne nahi deta."))
                ask("Rename?", renameMessage(name, req.newName.trim()), "Rename") {
                    try {
                        val n = DocumentsContract.renameDocument(contentResolver, uri, req.newName.trim())
                        done(if (n != null) StepResult(true, "'$name' ka naam '${req.newName.trim()}' ho gaya.", Verify.VERIFIED) else StepResult(false, "Rename nahi hua."))
                    } catch (e: Exception) { done(StepResult(false, "Rename nahi hua: Android ne roka.")) }
                }
            }
            FileOp.DELETE -> {
                if (flags(uri) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE == 0) return done(StepResult(false, "'$name' ko ye storage delete karne nahi deta."))
                ask("Permanently delete?", deleteMessage(name), "Delete") {
                    try {
                        val ok = DocumentsContract.deleteDocument(contentResolver, uri)
                        done(if (ok) StepResult(true, "'$name' delete ho gayi.", Verify.VERIFIED) else StepResult(false, "Delete nahi hua."))
                    } catch (e: Exception) { done(StepResult(false, "Delete nahi hua: Android ne roka.")) }
                }
            }
        }
    }

    private fun ask(title: String, msg: String, yes: String, onYes: () -> Unit) {
        confirmDialog(this, title, msg, yes, onYes) { done(StepResult(false, "Cancel kiya, kuch nahi badla.")) }
    }

    companion object {
        /** The real rename/delete confirmation dialog (also used by the render test). */
        fun confirmDialog(ctx: android.content.Context, title: String, msg: String, yes: String, onYes: () -> Unit, onCancel: () -> Unit): AlertDialog =
            AlertDialog.Builder(ctx).setTitle(title).setMessage(msg).setCancelable(true)
                .setPositiveButton(yes) { _, _ -> onYes() }
                .setNegativeButton("Cancel") { _, _ -> onCancel() }
                .setOnCancelListener { onCancel() }.show()
        fun deleteMessage(name: String) = "'$name'\n\nYe wapas nahi aayegi."
        fun renameMessage(old: String, new: String) = "'$old'  ->  '$new'"
    }

    private fun done(r: StepResult) {
        if (finished) return
        finished = true
        FileBridge.pending?.complete(r)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!finished && isFinishing) { finished = true; FileBridge.pending?.complete(StepResult(false, "File kaam adhoora chhoda gaya, kuch nahi badla.")) }
    }
}
