package com.chrisfry.socialq.enums

enum class SearchNavStep {
    // Enum grouping below describes different nav trees of search (starting at BASE)
    BASE,
    ALL_SONGS,
    ARTIST, ARTIST_ALL_ALBUM, ARTIST_ALBUM,
    ALL_ARTISTS, ALL_ARTIST_SELECTED, ALL_ARTIST_SELECTED_ALBUM,
    ALL_ARTIST_SELECTED_ALL_ALBUMS, ALL_ARTIST_SELECTED_ALL_ALBUM_SELECTED,
    ALBUM_SELECTED,
    ALL_ALBUMS, ALL_ALBUM_SELECTED
}