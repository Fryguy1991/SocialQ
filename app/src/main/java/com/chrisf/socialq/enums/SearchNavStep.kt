package com.chrisf.socialq.enums

enum class SearchNavStep {
    BASE,
    ALL_SONGS,
    ALL_ARTISTS, ARTIST,
    ALL_ALBUMS, ALBUM

    // NAV TREE
    //
    // BASE -> ALL_SONGS
    //      -> ARTIST -> ALBUM
    //                -> ALL_ALBUMS -> ALBUM
    //      -> ALL_ARTISTS -> ARTIST -> ALBUM
    //                               -> ALL_ALBUM -> ALBUM
    //      -> ALBUM
    //      -> ALL_ALBUMS -> ALBUM
}