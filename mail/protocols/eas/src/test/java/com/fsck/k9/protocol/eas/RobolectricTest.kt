package com.fsck.k9.protocol.eas

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class, manifest = Config.NONE)
abstract class RobolectricTest

class EmptyApplication : Application()
