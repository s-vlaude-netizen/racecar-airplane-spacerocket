package app.roadtoorbit.gfx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NebulaCubeTest {
    @Test fun facesAreTheRightSizeAndNotBlank() {
        val faces = NebulaCube.generate(32)
        assertEquals(6, faces.size)
        var lit = 0
        for (f in faces) {
            assertEquals(32 * 32 * 4, f.size)
            for (i in 0 until f.size step 4) if ((f[i].toInt() and 0xFF) + (f[i + 1].toInt() and 0xFF) + (f[i + 2].toInt() and 0xFF) > 20) lit++
        }
        assertTrue(lit > 200, "the nebula should light up part of the sky ($lit texels)")
    }

    @Test fun neighbouringFacesAgreeAlongTheirSharedEdges() {
        // the nebula is a continuous function of direction, so a correct face layout has no seams
        val n = 64
        val faces = NebulaCube.generate(n)
        val seam = NebulaCube.maxEdgeJump(faces, n)
        val interior = NebulaCube.maxInteriorJump(faces, n)
        assertTrue(seam <= interior * 1.35f + 0.02f, "visible seam between cube faces: seam=$seam vs interior=$interior")
    }
}
