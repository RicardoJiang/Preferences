package com.example.spholder.daily

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.spholder.R
import com.forjrking.preferences.kt.PreferenceHolder
import com.forjrking.preferences.serialize.GsonSerializer
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_test.*


class TestActivity : AppCompatActivity() {

    private val controller by lazy { TaskOneController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {

        PreferenceHolder.context = this.application
        PreferenceHolder.serializer = GsonSerializer(Gson())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        controller.setView(task1_ll)
        //设置下面的tip
        controller.setTip(task1_tip) {
            controller.setView(task1_ll)
        }
    }
}
