package com.cnanfc.xcanplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FondantDefaults {
    const val QT_URL = "https://www.fondant.kr/"
    const val BIBLE_URL = "https://www.fondant.kr/series/00090228-5db3-dc44-3c29-52bcaf0002ce"
}

data class ScheduleItem(val hour: Int, val minute: Int, val title: String, val url: String)

class LocalStore(context: Context) {
    // 과거 유튜브 찌꺼기 삭제를 위해 v3 저장소 사용
    private val prefs = context.getSharedPreferences("SchedulerPrefs_v4", Context.MODE_PRIVATE)

    fun loadSchedule(): MutableList<ScheduleItem> {
        val list = mutableListOf<ScheduleItem>()
        try {
            val array = JSONArray(prefs.getString("schedule_json", "[]"))
            if (array.length() == 0) {
                list.add(ScheduleItem(6, 0, "생명의삶", FondantDefaults.QT_URL))
                list.add(ScheduleItem(8, 0, "성경통독", FondantDefaults.BIBLE_URL))
            } else {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(ScheduleItem(obj.getInt("h"), obj.getInt("m"), obj.getString("t"), obj.getString("u")))
                }
            }
        } catch (e: Exception) { }
        return list
    }

    fun saveSchedule(list: List<ScheduleItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("h", item.hour); put("m", item.minute); put("t", item.title); put("u", item.url)
            }
            array.put(obj)
        }
        prefs.edit().putString("schedule_json", array.toString()).apply()
    }
}