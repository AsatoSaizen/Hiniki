package ani.himitsu.util

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import ani.himitsu.R
import ani.himitsu.buildMarkwon
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.databinding.ActivityMarkdownCreatorBinding
import ani.himitsu.initActivity
import ani.himitsu.navBarHeight
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import ani.himitsu.toast
import bit.himitsu.webkit.ChromeIntegration
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import tachiyomi.core.util.lang.launchIO

class MarkdownCreatorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMarkdownCreatorBinding
    private lateinit var type: String
    private var text: String = ""
    private var mediaId: Int = 0
    private var parentId: Int = 0
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)

        binding = ActivityMarkdownCreatorBinding.inflate(layoutInflater)
        binding.markdownCreatorToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        binding.buttonContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin += navBarHeight
        }
        setContentView(binding.root)
        if (intent.hasExtra("type")) {
            type = intent.getStringExtra("type")!!
        } else {
            toast("Error: No type")
            finish()
            return
        }
        binding.summaryText.isVisible = type == REVIEW
        binding.scoreText.isVisible = type == REVIEW
        binding.markdownCreatorTitle.text = when (type) {
            ACTIVITY -> getString(R.string.create_new_activity)
            REVIEW -> {
                mediaId = intent.getIntExtra("mediaId", -1)
                if (mediaId == -1) {
                    toast(R.string.error_no_media_id)
                    finish()
                    return
                }
                getString(R.string.create_new_review)
            }
            REPLY_ACTIVITY -> {
                parentId = intent.getIntExtra("parentId", -1)
                if (parentId == -1) {
                    toast(R.string.error_no_parent_id)
                    finish()
                    return
                }
                getString(R.string.create_new_reply)
            }
            else -> ""
        }
        binding.editText.setText(text)
        binding.editText.addTextChangedListener {
            if (!binding.markdownCreatorPreviewCheckbox.isChecked) {
                text = it.toString()
            }
        }
        previewMarkdown(false)
        binding.markdownCreatorPreviewCheckbox.setOnClickListener {
            previewMarkdown(binding.markdownCreatorPreviewCheckbox.isChecked)
        }
        binding.cancelButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.markdownCreatorBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.createButton.setOnClickListener {
            if (text.isBlank()) {
                toast(getString(R.string.cannot_be_empty))
                return@setOnClickListener
            }
            AlertDialogBuilder(this).apply {
                setTitle(R.string.warning)
                setMessage(R.string.post_to_anilist_warning)
                setPositiveButton(R.string.ok) {
                    launchIO {
                        val success = when (type) {
                            ACTIVITY -> AniList.mutation.postActivity(text)
                            REVIEW -> {
                                val summary = binding.summaryText.text?.toString()
                                val score = binding.scoreText.text?.toString()?.toIntOrNull()
                                if (summary.isNullOrBlank() || score == null) {
                                    toast(getString(R.string.cannot_be_empty))
                                    return@launchIO
                                }
                                AniList.mutation.postReview(summary, text, mediaId, score)
                            }
                            REPLY_ACTIVITY -> AniList.mutation.postReply(parentId, text)
                            else -> "Error: Unknown type"
                        }
                        toast(success)
                    }
                    onBackPressedDispatcher.onBackPressed()
                }
                setNeutralButton(R.string.open_rules) {
                    ChromeIntegration.openStreamTab(it.context, "https://anilist.co/forum/thread/14")
                }
                setNegativeButton(R.string.cancel)
            }.show()
        }

        binding.editText.requestFocus()
    }

    private fun previewMarkdown(preview: Boolean) {
        val markwon = buildMarkwon(this, false, anilist = true)
        if (preview) {
            binding.editText.isEnabled = false
            markwon.setMarkdown(binding.editText, text)
        } else {
            binding.editText.setText(text)
            binding.editText.isEnabled = true
            val markwonEditor = MarkwonEditor.create(markwon)
            binding.editText.addTextChangedListener(
                MarkwonEditorTextWatcher.withProcess(markwonEditor)
            )
        }
    }

    companion object {
        const val ACTIVITY = "activity"
        const val REVIEW = "review"
        const val REPLY_ACTIVITY = "replyActivity"
    }
}