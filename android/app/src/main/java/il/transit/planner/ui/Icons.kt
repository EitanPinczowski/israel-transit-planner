package il.transit.planner.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Material's "swap_vert", drawn here: it is only in the large extended icon set. */
val Icons.Filled.SwapVert: ImageVector
    get() = swapVert ?: materialIcon(name = "Filled.SwapVert") {
        materialPath {
            moveTo(16f, 17.01f)
            verticalLineTo(10f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(7.01f)
            horizontalLineToRelative(-3f)
            lineTo(15f, 21f)
            lineToRelative(4f, -3.99f)
            horizontalLineToRelative(-3f)
            close()
            moveTo(9f, 3f)
            lineTo(5f, 6.99f)
            horizontalLineToRelative(3f)
            verticalLineTo(14f)
            horizontalLineToRelative(2f)
            verticalLineTo(6.99f)
            horizontalLineToRelative(3f)
            lineTo(9f, 3f)
            close()
        }
    }.also { swapVert = it }

private var swapVert: ImageVector? = null
