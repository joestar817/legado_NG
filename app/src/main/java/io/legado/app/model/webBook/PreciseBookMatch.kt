package io.legado.app.model.webBook

/** The identity check shared by automatic source switching and typed book searches. */
internal fun matchesPreciseBook(
    name: String,
    author: String,
    expectedName: String,
    expectedAuthor: String,
): Boolean = name == expectedName && author == expectedAuthor
