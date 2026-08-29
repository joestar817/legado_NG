package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.Cookie

@Dao
interface CookieDao {

    @Query("SELECT * FROM cookies Where url = :url")
    fun get(url: String): Cookie?

    @Query("select * from cookies where url like '%|%'")
    fun getOkHttpCookies(): List<Cookie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg cookie: Cookie)

    @Update
    fun update(vararg cookie: Cookie)

    @Query("delete from cookies where url = :url")
    fun delete(url: String)

    @Query("select url from cookies")
    fun allUrls(): List<String>

    @Query("delete from cookies where url in (:urls)")
    fun deleteByUrls(urls: List<String>)

    @Query("delete from cookies where substr(url, 1, length(:prefix)) = :prefix")
    fun deleteByPrefix(prefix: String)

    @Query("delete from cookies where url like '%|%'")
    fun deleteOkHttp()
}

internal fun CookieDao.deleteByUrlsChunked(urls: Collection<String>) {
    urls.chunked(900).forEach(::deleteByUrls)
}
