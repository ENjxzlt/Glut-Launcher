package de.glut.launcher.data

import org.json.JSONArray
import org.json.JSONObject

data class Folder(val id: String, val name: String, val apps: List<String>) {
    /** Schlüssel, unter dem der Ordner in der Favoritenliste steht. */
    val favoriteKey: String get() = FAVORITE_PREFIX + id

    companion object {
        const val FAVORITE_PREFIX = "folder:"

        fun decodeList(json: String): List<Folder> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val apps = obj.getJSONArray("apps")
                Folder(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    apps = (0 until apps.length()).map { apps.getString(it) },
                )
            }
        }.getOrDefault(emptyList())

        fun encodeList(folders: List<Folder>): String =
            JSONArray().apply {
                folders.forEach { folder ->
                    put(
                        JSONObject()
                            .put("id", folder.id)
                            .put("name", folder.name)
                            .put("apps", JSONArray(folder.apps)),
                    )
                }
            }.toString()
    }
}
