package com.grindrplus.core

object Constants {
    const val NEWLINE = "GRINDRPLUS_NEWLINE"
    const val GRINDR_PACKAGE_NAME = "com.grindrapp.android"

    val DEFAULT_SPLINE_POINTS = listOf(
        1238563200L to 0,          // 2009-04-01
        1285027200L to 1000000,    // 2010-09-21
        1462924800L to 35512000,   // 2016-05-11
        1501804800L to 132076000,  // 2017-08-04
        1546547829L to 201948000,  // 2019-01-03
        1618531200L to 351220000,  // 2021-04-16
        1636150385L to 390338000,  // 2021-11-05
        1637963460L to 394800000,  // 2021-11-26
        1680393600L to 505225000,  // 2023-04-02
        1717200000L to 630495000,  // 2024-06-01
        1717372800L to 634942000,  // 2024-06-03
        1729950240L to 699724000,  // 2024-10-26
        1732986600L to 710609000,  // 2024-11-30
        1733349060L to 711676000,  // 2024-12-04
        1735229820L to 718934000,  // 2024-12-26
        1738065780L to 730248000,  // 2025-01-29
        1739059200L to 733779000,  // 2025-02-09
        1741564800L to 744008000   // 2025-03-10
    )

    const val SPLINE_DATA_ENDPOINT =
        "https://raw.githubusercontent.com/R0rt1z2/GrindrPlus/refs/heads/master/spline.json"
}