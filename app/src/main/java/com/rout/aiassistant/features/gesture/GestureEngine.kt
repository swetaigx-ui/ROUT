package com.rout.aiassistant.features.gesture

import android.content.Context
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot

/** MediaPipe hand landmarker -> simple gestures. Model: assets/hand_landmarker.task (Google, public). */
class GestureEngine(context: Context) {
    enum class Gesture(val label: String) { OPEN_PALM("Khuli muthi (5)"), FIST("Muthi (0)"), VICTORY("Victory (2)"), THREE("Teen (3)"), THUMB_UP("Thumbs up"), NONE("-") }

    private val landmarker: HandLandmarker = HandLandmarker.createFromOptions(
        context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(com.google.mediapipe.tasks.core.BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(1)
            .build()
    )

    fun detect(mpImage: com.google.mediapipe.framework.image.MPImage): Gesture {
        val res: HandLandmarkerResult = landmarker.detect(mpImage)
        val hands = res.landmarks()
        if (hands.isEmpty()) return Gesture.NONE
        val lm: List<NormalizedLandmark> = hands[0]
        // wrist = 0; fingertips 8,12,16,20; thumb tip 4; pip joints 6,10,14,18
        fun dist(i: Int, j: Int) = hypot(lm[i].x() - lm[j].x(), lm[i].y() - lm[j].y())
        var fingers = 0
        // four fingers: tip above pip (image y down)
        if (lm[8].y() < lm[6].y() - 0.02f) fingers++
        if (lm[12].y() < lm[10].y() - 0.02f) fingers++
        if (lm[16].y() < lm[14].y() - 0.02f) fingers++
        if (lm[20].y() < lm[18].y() - 0.02f) fingers++
        val thumbOut = dist(4, 17) > dist(2, 17) + 0.03f
        return when {
            fingers == 4 && thumbOut -> Gesture.OPEN_PALM
            fingers == 0 && !thumbOut -> Gesture.FIST
            fingers == 0 && thumbOut -> Gesture.THUMB_UP
            fingers == 2 -> Gesture.VICTORY
            fingers == 3 -> Gesture.THREE
            else -> Gesture.NONE
        }
    }

    fun close() = landmarker.close()
}
