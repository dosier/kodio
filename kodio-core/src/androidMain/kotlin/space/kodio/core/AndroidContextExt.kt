package space.kodio.core

import android.content.Context
import android.media.AudioManager

internal val Context.audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager