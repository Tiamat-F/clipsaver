package com.clipsaver.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val url: String = "",
    val busy: Boolean = false,
    val progress: Float = 0f,       // 0..100, or -1 while indeterminate
    val status: String = "",
    val isError: Boolean = false,
    val saved: List<SavedVideo> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var job: Job? = null

    fun onUrlChange(value: String) = _state.update { it.copy(url = value) }

    /** Called with text shared from TikTok; fills the box and starts right away. */
    fun onShared(text: String?) {
        val url = Downloader.extractUrl(text) ?: return
        _state.update { it.copy(url = url) }
        download()
    }

    fun download() {
        val url = Downloader.extractUrl(_state.value.url)
        if (url == null) {
            _state.update { it.copy(status = "Paste a TikTok video link first.", isError = true) }
            return
        }
        if (job?.isActive == true) return

        job = viewModelScope.launch {
            _state.update {
                it.copy(busy = true, progress = -1f, status = "Preparing…", isError = false)
            }
            try {
                val video = Downloader.download(getApplication(), url) { percent, _ ->
                    _state.update {
                        it.copy(
                            progress = if (percent > 0f) percent else -1f,
                            status = if (percent > 0f) "Downloading ${percent.toInt()}%" else "Fetching video info…",
                        )
                    }
                }
                _state.update {
                    it.copy(
                        busy = false, progress = 100f, url = "",
                        status = "Saved to Movies/ClipSaver",
                        saved = listOf(video) + it.saved,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: YoutubeDL.CanceledException) {
                _state.update { it.copy(busy = false, progress = 0f, status = "Cancelled.", isError = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, progress = 0f, status = friendlyError(e), isError = true)
                }
            }
        }
    }

    fun cancel() {
        Downloader.cancel()
    }

    fun updateEngine() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(busy = true, progress = -1f, status = "Updating download engine…", isError = false) }
            val msg = runCatching { Downloader.updateEngine(getApplication()) }
                .getOrElse { "Update failed: ${it.message ?: "unknown error"}" }
            _state.update { it.copy(busy = false, progress = 0f, status = msg, isError = msg.startsWith("Update failed")) }
        }
    }

    private fun friendlyError(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            "Unable to resolve host" in raw || "Network is unreachable" in raw ->
                "No internet connection."
            "private" in raw.lowercase() || "login" in raw.lowercase() ->
                "This video is private or needs a login, so it can't be downloaded."
            "Unsupported URL" in raw ->
                "That link isn't a supported video link."
            else ->
                "Download failed. Try “Update engine” from the menu, then retry.\n\n" +
                    raw.lines().lastOrNull { it.contains("ERROR") }.orEmpty().take(300)
        }
    }
}
