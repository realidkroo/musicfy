package com.example.musicfy.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/*
 * AGSL shader plumbing, kept behind `Any`.
 *
 * `android.graphics.RuntimeShader` is API 33. Naming it in a parameter, return type or field
 * means ART has to resolve the class when that method is reached - and on Android 8 that is a
 * `NoClassDefFoundError: Failed resolution of: Landroid/graphics/RuntimeShader;` at launch, even
 * though every *call* is already behind an SDK check. Guarding the call is not enough; the type
 * must not appear in any signature reachable on an older device.
 *
 * So shaders travel as `Any` and only these helpers - each annotated, each invoked solely from
 * inside a version check - ever name the real type.
 */

@RequiresApi(33)
internal fun createAgslShader(source: String): Any = RuntimeShader(source)

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, value: Float) {
    (this as RuntimeShader).setFloatUniform(name, value)
}

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, first: Float, second: Float) {
    (this as RuntimeShader).setFloatUniform(name, first, second)
}

@RequiresApi(33)
internal fun agslRenderEffect(shader: Any, inputName: String): RenderEffect =
    RenderEffect.createRuntimeShaderEffect(shader as RuntimeShader, inputName)
