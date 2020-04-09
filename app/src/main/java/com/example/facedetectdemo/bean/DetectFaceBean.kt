package com.example.facedetectdemo.bean

data class DetectFaceBean(
    val face_num: Int,
    val face_list: List<DetectFaceBase>
)

data class DetectFaceBase(
    val face_token: String,
    val location: DetectFaceLocation,
    val face_probability: Double,
    val angle: DetectFaceAngle,
    val age: Double,
    val beauty: Double,
    val expression: DetectFaceExpression,
    val gender: DetectFaceGender,
    val race: DetectFaceRace
)

data class DetectFaceLocation(
    val left: Double, val top: Double, val width: Double, val height: Double, val rotation: Double
)

data class DetectFaceAngle(
    val yaw: Double, val pitch: Double, val roll: Double
)

data class DetectFaceExpression (
    val type: String, val probability: Double
)

data class DetectFaceGender(
    val type: String, val probability: Double
)

data class DetectFaceRace(
    val type: String, val probability: Double
)

