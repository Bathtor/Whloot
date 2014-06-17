package com.larskroll.whloot.data

case class SolarSystem(id: Int, name: String, sec: Float)
case class FullSolarSystem(id: Int, name: String, trueSec: Double, x: Double, y: Double, sec: Float, z: Double)
case class Route(length: Int, path: List[SolarSystem])
case class MultiRoute(start: SolarSystem, routes: Map[SolarSystem, Route])