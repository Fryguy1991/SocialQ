package com.chrisfry.socialq.enums

enum class SearchNavStep {
    // Enum grouping below describes different nav trees of search (starting at BASE)
    BASE,
    VIEW_ALL_SONGS,
    ARTIST_SELECTED, ARTIST_ALBUM_SELECTED,
    VIEW_ALL_ARTISTS, VIEW_ALL_ARTIST_SELECTED, VIEW_ALL_ARTIST_ALBUM_SELECTED,
    ALBUM_SELECTED,
    VIEW_ALL_ALBUMS, VIEW_ALL_ALBUM_SELECTED
}