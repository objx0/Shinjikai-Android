package com.shinjikai.dictionary

import android.app.Application
import android.content.Context

class ShinjikaiApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LatinDigits.wrap(base))
    }
}
