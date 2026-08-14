package il.cet.bonus.audio

import il.cet.bonus.R

/**
 * The music catalog for the old/new theme switch. Each [MusicTrack] is identified by a
 * stable [key] (the CMF track name, where known) and has an "old" style raw resource,
 * a "new" style raw resource, or both.
 *
 * IMPORTANT (flag for CET/user): only 7 of the 14 original CMF tracks have a confirmed
 * old+new pair with matching content (Felicity, Sambmoka - old only, Concerto3,
 * Entertainer, Maple Leaf, Egyptienne, Cantina, Solfeggietto). The remaining old tracks
 * (Sambmoka, Hooked, Passage, Mamb, Business, Title, Help) have no "new style" rendition
 * available, and several "new" tracks (Badinerie, Moonlight Sonata, Eine Kleine
 * Nachtmusik, Toreadors, Prince of Denmark, Romeo & Juliet, Hallelujah, Turkish March,
 * Symphony 40, Tchaikovsky Concerto 1, Tico-Tico) have no "old style" rendition. The
 * theme system is therefore built to play whichever set is available per theme rather
 * than assuming strict 1:1 pairing - when switching theme, a track without a rendition
 * in the new theme simply falls back to skipping to the next available track.
 */
data class MusicTrack(
    val key: String,
    val oldRes: Int?,
    val newRes: Int?,
)

object MusicCatalog {
    val tracks: List<MusicTrack> = listOf(
        MusicTrack("felicity", R.raw.felicity_old, R.raw.felicity_new),
        MusicTrack("concerto3", R.raw.concerto3_old, R.raw.concerto3_new),
        MusicTrack("entertainer", R.raw.entertainer_old, R.raw.entertainer_new),
        MusicTrack("maple_leaf", R.raw.maple_leaf_old, R.raw.maple_leaf_new),
        MusicTrack("egyptienne", R.raw.egyptienne_old, R.raw.egyptienne_new),
        MusicTrack("cantina", R.raw.cantina_old, R.raw.cantina_new),
        MusicTrack("solfeggietto", R.raw.solfeggietto_old, R.raw.solfeggietto_new),
        // Old-only (no confirmed "new style" rendition of the same recording).
        MusicTrack("sambmoka", R.raw.sambmoka_old, null),
        MusicTrack("hooked", R.raw.hooked_old, null),
        MusicTrack("passage", R.raw.passage_old, null),
        MusicTrack("mamb", R.raw.mamb_old, null),
        MusicTrack("business", R.raw.business_old, null),
        MusicTrack("title", R.raw.title_old, null),
        MusicTrack("help", R.raw.help_old, null),
        // New-only extra tracks (not tied to a confirmed original CMF track).
        MusicTrack("badinerie", null, R.raw.badinerie_new),
        MusicTrack("moonlight_sonata", null, R.raw.moonlight_sonata_new),
        MusicTrack("eine_kleine_nachtmusik", null, R.raw.eine_kleine_nachtmusik_new),
        MusicTrack("toreadors", null, R.raw.toreadors_new),
        MusicTrack("prince_of_denmark", null, R.raw.prince_of_denmark_new),
        MusicTrack("romeo_juliet", null, R.raw.romeo_juliet_new),
        MusicTrack("hallelujah", null, R.raw.hallelujah_new),
        MusicTrack("turkish_march", null, R.raw.turkish_march_new),
        MusicTrack("symphony_40", null, R.raw.symphony_40_new),
        MusicTrack("tchaikovsky_concerto1", null, R.raw.tchaikovsky_concerto1_new),
        MusicTrack("tico_tico", null, R.raw.tico_tico_new),
        MusicTrack("fantaisie_impromptu", null, R.raw.fantaisie_impromptu_new),
    )

    enum class Theme { OLD, NEW }

    /** Resource ids playable under [theme], in order, skipping tracks with no rendition for it. */
    fun playlist(theme: Theme): List<Int> = tracks.mapNotNull { t ->
        when (theme) {
            Theme.OLD -> t.oldRes
            Theme.NEW -> t.newRes
        }
    }
}
