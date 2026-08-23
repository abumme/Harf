package uz.abumme.harfgame.feature.share

/** Platform share + clipboard. Real on Android/iOS; best-effort (copy) on desktop/web. */
interface Sharer {
    fun copy(text: String)
    fun share(text: String)
}
