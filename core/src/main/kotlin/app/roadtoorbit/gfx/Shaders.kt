package app.roadtoorbit.gfx

/** GLSL ES 3.00 sources. Everything is procedural: there are no textures in this game. */
object Shaders {

    // The "curved world" trick: after the view transform every vertex is pushed sideways/down in
    // proportion to the square of its distance, so a dead-straight world looks like winding,
    // rolling roads and a curving planet horizon.
    const val BEND = """
    uniform vec3 uBend; // x: lateral curvature, y: vertical curvature, z: distance where bending starts
    vec4 applyBend(vec4 v) {
        float d = max(-v.z - uBend.z, 0.0);
        v.x += uBend.x * d * d;
        v.y += uBend.y * d * d;
        return v;
    }
    """

    const val LIT_VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec4 aColor;
uniform mat4 uModel;
uniform mat3 uNormalMat;
uniform mat4 uView;
uniform mat4 uProj;
$BEND
out vec3 vNormal;
out vec4 vColor;
out vec3 vWorld;
out float vDist;
void main() {
    vec4 world = uModel * vec4(aPos, 1.0);
    vec4 view = applyBend(uView * world);
    gl_Position = uProj * view;
    vNormal = uNormalMat * aNormal;
    vColor = aColor;
    vWorld = world.xyz;
    vDist = length(view.xyz);
}
"""

    const val LIT_FRAG = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec4 vColor;
in vec3 vWorld;
in float vDist;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uAmbSky;
uniform vec3 uAmbGround;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform vec4 uTint;
uniform vec4 uMat; // x: specular strength, y: shininess, z: emissive 0..1, w: fog scale
uniform vec4 uRim; // rgb: rim colour, a: rim power (0 = off)
out vec4 outColor;
void main() {
    vec3 n = normalize(vNormal);
    vec3 base = vColor.rgb * uTint.rgb;
    vec3 toCam = normalize(uCamPos - vWorld);
    float ndl = max(dot(n, uSunDir), 0.0);
    vec3 amb = mix(uAmbGround, uAmbSky, n.y * 0.5 + 0.5);
    vec3 lit = base * (amb + uSunColor * ndl);
    if (uMat.x > 0.0) {
        vec3 h = normalize(uSunDir + toCam);
        float s = pow(max(dot(n, h), 0.0), uMat.y) * uMat.x;
        lit += uSunColor * s * step(0.001, ndl);
    }
    if (uRim.a > 0.0) {
        float f = pow(1.0 - max(dot(n, toCam), 0.0), uRim.a);
        lit += uRim.rgb * f;
    }
    lit = mix(lit, base, uMat.z);
    float fd = vDist * uFogDensity * uMat.w;
    float fog = clamp(1.0 - exp(-fd * fd), 0.0, 1.0);
    lit = mix(lit, uFogColor, fog);
    outColor = vec4(lit, vColor.a * uTint.a);
}
"""

    const val SKY_VERT = """#version 300 es
out vec2 vNdc;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vNdc = p * 2.0 - 1.0;
    gl_Position = vec4(vNdc, 1.0, 1.0);
}
"""

    const val SKY_FRAG = """#version 300 es
precision highp float;
in vec2 vNdc;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform vec3 uCamFwd;
uniform vec2 uTanFov; // x: tan(fovY/2)*aspect, y: tan(fovY/2)
uniform vec3 uZenith;
uniform vec3 uHorizon;
uniform vec3 uGroundCol;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec4 uSky; // x: star amount, y: nebula amount, z: time, w: sun disc size
uniform samplerCube uNebula;
out vec4 outColor;

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0));
    float k = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

float starLayer(vec3 dir, float cells, float prob, float radius) {
    vec3 p = dir * cells;
    vec3 id = floor(p);
    vec3 f = p - id;
    float r = hash13(id);
    if (r > prob) return 0.0;
    vec3 c = vec3(hash13(id + 17.1), hash13(id + 31.7), hash13(id + 53.3)) * 0.6 + 0.2;
    float dd = length(f - c);
    float tw = 0.72 + 0.28 * sin(uSky.z * (1.5 + r * 30.0) + r * 120.0);
    return smoothstep(radius, radius * 0.15, dd) * tw * (0.45 + 0.55 * hash13(id + 7.7));
}

void main() {
    vec3 dir = normalize(uCamFwd + uCamRight * (vNdc.x * uTanFov.x) + uCamUp * (vNdc.y * uTanFov.y));
    float h = dir.y;
    vec3 col;
    if (h >= 0.0) {
        col = mix(uHorizon, uZenith, pow(h, 0.5));
    } else {
        col = mix(uHorizon, uGroundCol, pow(-h, 0.35));
    }
    float sd = max(dot(dir, uSunDir), 0.0);
    col += uSunColor * (pow(sd, 6.0) * 0.16 + pow(sd, 48.0) * 0.35);
    if (uSky.w > 0.0) {
        col += uSunColor * smoothstep(1.0 - uSky.w, 1.0 - uSky.w * 0.5, sd) * 1.6;
    }
    if (uSky.x > 0.001) {
        float s = starLayer(dir, 55.0, 0.11, 0.16) + 0.7 * starLayer(dir, 120.0, 0.16, 0.22);
        vec3 starCol = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.9, 0.75), hash13(floor(dir * 55.0) + 3.0));
        col += starCol * s * uSky.x * 1.4;
        if (uSky.y > 0.001) {
            col += texture(uNebula, dir).rgb * (uSky.y * 0.36);
        }
    }
    outColor = vec4(col, 1.0);
}
"""

    /**
     * Procedural terrain: the vertex shader generates a grid straight from gl_VertexID (6 vertices
     * per cell, no vertex buffer at all) and displaces it with noise anchored to world coordinates,
     * so hills slide past without swimming. Flat shading comes from screen-space derivatives.
     */
    const val TERRAIN_VERT = """#version 300 es
uniform mat4 uView;
uniform mat4 uProj;
$BEND
uniform vec4 uGrid;   // x: cell size, y: columns, z: rows behind the camera, w: world row index of local row 0
uniform float uSlide; // shift along +z (0..cell) keeping vertices anchored to the world
uniform vec4 uTer;    // x: ground y, y: amplitude, z: flat corridor half width, w: style (0 grass, 1 cloud, 2 moon)
out vec3 vWorld;
out float vDist;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + 7.1;
        a *= 0.5;
    }
    return s;
}
float craters(vec2 p, float cell) {
    vec2 q = p / cell;
    vec2 g = floor(q);
    vec2 f = fract(q);
    float h = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 o = vec2(float(i), float(j));
            vec2 id = g + o;
            float rnd = hash12(id);
            if (rnd < 0.5) continue;
            vec2 c = o + vec2(hash12(id + 3.7), hash12(id + 9.1)) * 0.6 + 0.2 - f;
            float r = 0.22 + 0.22 * hash12(id + 5.3);
            float d = length(c) / r;
            float bowl = -(1.0 - smoothstep(0.0, 1.0, d)) * 0.8;
            float rim = smoothstep(0.7, 1.0, d) * (1.0 - smoothstep(1.0, 1.5, d)) * 0.35;
            h += (bowl + rim) * r * cell * 0.5;
        }
    }
    return h;
}
float grassHeight(vec2 xs) {
    float ax = abs(xs.x);
    float valley = smoothstep(uTer.z, uTer.z + 130.0, ax);
    float ridge = 1.0 - abs(fbm(xs * 0.006 + 40.0) * 2.0 - 1.0);
    float h = (fbm(xs * 0.011) * 0.55 + ridge * ridge * 0.9) * uTer.y * valley;
    h += (fbm(xs * 0.09) - 0.5) * 1.4 * smoothstep(uTer.z - 2.0, uTer.z + 6.0, ax);
    return h;
}
float cloudHeight(vec2 xs) {
    float b = fbm(xs * 0.02 + 5.0);
    return b * b * 30.0 + fbm(xs * 0.08) * 3.0;
}
float moonHeight(vec2 xs) {
    float flat_ = smoothstep(14.0, 54.0, abs(xs.x));
    return (craters(xs, 22.0) * 1.6 + craters(xs + 130.0, 60.0) * 2.2 + (fbm(xs * 0.05) - 0.5) * 3.0) * mix(0.35, 1.0, flat_);
}
float terrainHeight(vec2 xs) {
    float st = uTer.w;
    if (st <= 0.0) return grassHeight(xs);
    if (st < 1.0) return mix(grassHeight(xs), cloudHeight(xs), st);
    if (st < 2.0) return mix(cloudHeight(xs), moonHeight(xs), st - 1.0);
    return moonHeight(xs);
}

void main() {
    // indexed grid: gl_VertexID is the vertex index (cols + 1 vertices per row)
    int stride = int(uGrid.y) + 1;
    int row = gl_VertexID / stride;
    int col = gl_VertexID - row * stride;
    float gx = (float(col) - uGrid.y * 0.5) * uGrid.x;
    float gr = float(row);
    float route = (uGrid.w + gr) * uGrid.x;
    float z = uSlide + (uGrid.z - gr) * uGrid.x;
    vec3 world = vec3(gx, uTer.x + terrainHeight(vec2(gx, route)), z);
    vec4 view = applyBend(uView * vec4(world, 1.0));
    gl_Position = uProj * view;
    vWorld = world;
    vDist = length(view.xyz);
}
"""

    const val TERRAIN_FRAG = """#version 300 es
precision highp float;
in vec3 vWorld;
in float vDist;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uAmbSky;
uniform vec3 uAmbGround;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform vec4 uTer;    // x: ground y, y: amplitude, z: corridor half width, w: style
uniform vec4 uTerCol; // rgb: tint multiplier, a: unused
out vec4 outColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

void main() {
    vec3 n = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
    if (n.y < 0.0) n = -n;
    float st = uTer.w;
    float amp = mix(max(uTer.y, 1.0), 30.0, clamp(st, 0.0, 1.0));
    float hn = clamp((vWorld.y - uTer.x) / amp, 0.0, 1.5);
    float blotch = vnoise(vWorld.xz * 0.045);
    float speck = 0.5;
    if (vDist < 170.0) speck = vnoise(vWorld.xz * 0.35); // fine detail only where it can be seen

    vec3 grassA = vec3(0.27, 0.52, 0.20);
    vec3 grassB = vec3(0.40, 0.60, 0.22);
    vec3 forest = vec3(0.15, 0.36, 0.17);
    vec3 rock = vec3(0.47, 0.43, 0.40);
    vec3 snow = vec3(0.93, 0.95, 0.98);
    vec3 grass = mix(grassA, grassB, blotch);
    float woods = smoothstep(0.3, 0.7, blotch * 0.55 + 0.45 * (0.5 + 0.5 * sin(vWorld.x * 0.013 + vWorld.z * 0.0071)));
    grass = mix(grass, forest, woods * (1.0 - smoothstep(0.2, 0.5, hn)));
    grass = mix(grass, rock, smoothstep(0.28, 0.5, hn + (blotch - 0.5) * 0.2));
    grass = mix(grass, snow, smoothstep(0.62, 0.78, hn + (speck - 0.5) * 0.08));
    float verge = 1.0 - smoothstep(7.0, 12.0, abs(vWorld.x));
    grass = mix(grass, vec3(0.36, 0.42, 0.22), verge * 0.55);
    grass *= 0.93 + 0.14 * speck;

    vec3 cloudCol = mix(vec3(0.66, 0.74, 0.90), vec3(1.0, 1.0, 1.0), smoothstep(0.0, 0.7, hn + (blotch - 0.5) * 0.4));

    vec3 dust = vec3(0.60, 0.59, 0.58) * (0.82 + 0.3 * blotch) * (0.9 + 0.2 * speck);
    vec3 col = mix(grass, cloudCol, clamp(st, 0.0, 1.0));
    col = mix(col, dust, clamp(st - 1.0, 0.0, 1.0));
    col *= uTerCol.rgb;
    float ndl = max(dot(n, uSunDir), 0.0);
    vec3 amb = mix(uAmbGround, uAmbSky, n.y * 0.5 + 0.5);
    vec3 lit = col * (amb + uSunColor * ndl);
    float fd = vDist * uFogDensity;
    float fog = clamp(1.0 - exp(-fd * fd), 0.0, 1.0);
    outColor = vec4(mix(lit, uFogColor, fog), 1.0);
}
"""

    const val PARTICLE_VERT = """#version 300 es
layout(location = 0) in vec4 aPosSize; // xyz + size in world units
layout(location = 1) in vec4 aColor;
uniform mat4 uView;
uniform mat4 uProj;
$BEND
uniform float uPixelScale; // viewport height / (2 * tan(fovY/2))
out vec4 vColor;
void main() {
    vec4 view = applyBend(uView * vec4(aPosSize.xyz, 1.0));
    gl_Position = uProj * view;
    float dist = max(-view.z, 0.25);
    gl_PointSize = clamp(aPosSize.w * uPixelScale / dist, 1.0, 200.0);
    vColor = aColor;
}
"""

    const val PARTICLE_FRAG = """#version 300 es
precision mediump float;
in vec4 vColor;
out vec4 outColor;
void main() {
    vec2 q = gl_PointCoord * 2.0 - 1.0;
    float d2 = dot(q, q);
    if (d2 > 1.0) discard;
    float a = 1.0 - d2;
    outColor = vec4(vColor.rgb, vColor.a * a * a);
}
"""
}
