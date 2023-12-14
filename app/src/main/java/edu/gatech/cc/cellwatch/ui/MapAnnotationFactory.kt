package edu.gatech.cc.cellwatch.ui


data class Coordinates(val latitude: Double, val longitude: Double)

class MapAnnotationFactory(private val allCoordinates: List<Coordinates>) {
    private val thresholdDistance = 0.01

    private fun areCloseEnough(coord1: Coordinates, coord2: Coordinates): Boolean {
        val distanceLat = Math.abs(coord1.latitude - coord2.latitude)
        val distanceLng = Math.abs(coord1.longitude - coord2.longitude)
        return distanceLat < thresholdDistance && distanceLng < thresholdDistance
    }

    private fun groupCoordinates(): List<Coordinates> {
        val groupedCoordinates = mutableListOf<Coordinates>()
        val visited = BooleanArray(allCoordinates.size)

        for (i in allCoordinates.indices) {
            if (!visited[i]) {
                visited[i] = true
                var groupCenterLat = allCoordinates[i].latitude
                var groupCenterLng = allCoordinates[i].longitude
                var groupCount = 1

                for (j in i + 1 until allCoordinates.size) {
                    if (!visited[j] && areCloseEnough(allCoordinates[i], allCoordinates[j])) {
                        groupCenterLat += allCoordinates[j].latitude
                        groupCenterLng += allCoordinates[j].longitude
                        groupCount++
                        visited[j] = true
                    }
                }

                if (groupCount > 1) {
                    groupedCoordinates.add(Coordinates(groupCenterLat / groupCount, groupCenterLng / groupCount))
                } else {
                    groupedCoordinates.add(allCoordinates[i])
                }
            }
        }

        return groupedCoordinates
    }

    fun getAnnotations(): List<Coordinates> = groupCoordinates()
}
