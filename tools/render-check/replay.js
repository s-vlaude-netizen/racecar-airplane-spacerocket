// Replays a binary GL command trace (see RecordingGles.kt) on a WebGL2 canvas.
// Used only for the headless render check: it lets us look at what the game renderer produces
// (and compile its GLSL ES 3.00 shaders in a real implementation) without an Android device.
const Op = {
  CREATE_SHADER: 1, SHADER_SOURCE: 2, COMPILE_SHADER: 3, DELETE_SHADER: 4, CREATE_PROGRAM: 5,
  ATTACH_SHADER: 6, LINK_PROGRAM: 7, USE_PROGRAM: 8, DELETE_PROGRAM: 9, GET_UNIFORM_LOCATION: 10,
  GEN_BUFFER: 11, BIND_BUFFER: 12, BUFFER_DATA: 13, BUFFER_SUB_DATA: 14, DELETE_BUFFER: 15,
  GEN_VERTEX_ARRAY: 16, BIND_VERTEX_ARRAY: 17, DELETE_VERTEX_ARRAY: 18, ENABLE_VERTEX_ATTRIB: 19,
  DISABLE_VERTEX_ATTRIB: 20, VERTEX_ATTRIB_POINTER: 21, UNIFORM_1I: 22, UNIFORM_1F: 23,
  UNIFORM_2F: 24, UNIFORM_3F: 25, UNIFORM_4F: 26, UNIFORM_MATRIX_3: 27, UNIFORM_MATRIX_4: 28,
  ENABLE: 30, DISABLE: 31, DEPTH_FUNC: 32, DEPTH_MASK: 33, BLEND_FUNC: 34, CULL_FACE: 35,
  VIEWPORT: 36, CLEAR_COLOR: 37, CLEAR: 38, DRAW_ARRAYS: 40, DRAW_ELEMENTS: 41,
  GEN_TEXTURE: 50, DELETE_TEXTURE: 51, ACTIVE_TEXTURE: 52, BIND_TEXTURE: 53, TEX_PARAMETER: 54, TEX_IMAGE_2D: 55,
  GENERATE_MIPMAP: 56, FRAME_END: 99,
};

window.replayTrace = function (base64, captureFrames) {
  const bin = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  const dv = new DataView(bin.buffer);
  let p = 0;
  const i32 = () => { const v = dv.getInt32(p, true); p += 4; return v; };
  const f32 = () => { const v = dv.getFloat32(p, true); p += 4; return v; };
  const u8 = () => dv.getUint8(p++);
  const str = () => { const n = i32(); const s = new TextDecoder().decode(bin.subarray(p, p + n)); p += n; return s; };
  const blob = () => { const n = i32(); const b = bin.slice(p, p + n); p += n; return b; };

  if (i32() !== 0x474c5452) throw new Error('bad trace magic');
  const width = i32(), height = i32();
  const canvas = document.getElementById('c');
  canvas.width = width; canvas.height = height;
  const gl = canvas.getContext('webgl2', { antialias: true, preserveDrawingBuffer: true, alpha: false });
  if (!gl) throw new Error('WebGL2 unavailable');

  const errors = [];
  const objs = new Map();      // trace id -> WebGL object
  const locs = new Map();      // trace location id -> WebGLUniformLocation | null
  const get = (id) => (id === 0 ? null : objs.get(id));
  const want = new Set(captureFrames);
  const shots = [];
  let frame = 0;
  let capturing = want.has(0);
  let draws = 0;

  while (p < bin.length) {
    const op = u8();
    switch (op) {
      case Op.CREATE_SHADER: { const id = i32(); objs.set(id, gl.createShader(i32())); break; }
      case Op.SHADER_SOURCE: { const id = i32(); gl.shaderSource(objs.get(id), str()); break; }
      case Op.COMPILE_SHADER: {
        const id = i32(); const sh = objs.get(id); gl.compileShader(sh);
        if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) errors.push('shader compile: ' + gl.getShaderInfoLog(sh));
        break;
      }
      case Op.DELETE_SHADER: { const id = i32(); gl.deleteShader(objs.get(id)); break; }
      case Op.CREATE_PROGRAM: { const id = i32(); objs.set(id, gl.createProgram()); break; }
      case Op.ATTACH_SHADER: { const pr = i32(), sh = i32(); gl.attachShader(objs.get(pr), objs.get(sh)); break; }
      case Op.LINK_PROGRAM: {
        const id = i32(); const pr = objs.get(id); gl.linkProgram(pr);
        if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) errors.push('program link: ' + gl.getProgramInfoLog(pr));
        break;
      }
      case Op.USE_PROGRAM: gl.useProgram(get(i32())); break;
      case Op.DELETE_PROGRAM: { const id = i32(); gl.deleteProgram(objs.get(id)); break; }
      case Op.GET_UNIFORM_LOCATION: { const pr = i32(); const name = str(); const id = i32(); locs.set(id, gl.getUniformLocation(objs.get(pr), name)); break; }
      case Op.GEN_BUFFER: { const id = i32(); objs.set(id, gl.createBuffer()); break; }
      case Op.BIND_BUFFER: { const t = i32(); gl.bindBuffer(t, get(i32())); break; }
      case Op.BUFFER_DATA: {
        const t = i32(), size = i32(), usage = i32(), has = i32();
        if (has) gl.bufferData(t, blob(), usage); else gl.bufferData(t, size, usage);
        break;
      }
      case Op.BUFFER_SUB_DATA: { const t = i32(), off = i32(); gl.bufferSubData(t, off, blob()); break; }
      case Op.DELETE_BUFFER: { const id = i32(); gl.deleteBuffer(objs.get(id)); break; }
      case Op.GEN_VERTEX_ARRAY: { const id = i32(); objs.set(id, gl.createVertexArray()); break; }
      case Op.BIND_VERTEX_ARRAY: gl.bindVertexArray(get(i32())); break;
      case Op.DELETE_VERTEX_ARRAY: { const id = i32(); gl.deleteVertexArray(objs.get(id)); break; }
      case Op.ENABLE_VERTEX_ATTRIB: gl.enableVertexAttribArray(i32()); break;
      case Op.DISABLE_VERTEX_ATTRIB: gl.disableVertexAttribArray(i32()); break;
      case Op.VERTEX_ATTRIB_POINTER: { const a = i32(), s = i32(), t = i32(), n = i32(), st = i32(), off = i32(); gl.vertexAttribPointer(a, s, t, n !== 0, st, off); break; }
      case Op.UNIFORM_1I: { const l = locs.get(i32()); gl.uniform1i(l, i32()); break; }
      case Op.UNIFORM_1F: { const l = locs.get(i32()); gl.uniform1f(l, f32()); break; }
      case Op.UNIFORM_2F: { const l = locs.get(i32()); gl.uniform2f(l, f32(), f32()); break; }
      case Op.UNIFORM_3F: { const l = locs.get(i32()); gl.uniform3f(l, f32(), f32(), f32()); break; }
      case Op.UNIFORM_4F: { const l = locs.get(i32()); gl.uniform4f(l, f32(), f32(), f32(), f32()); break; }
      case Op.UNIFORM_MATRIX_3: { const l = locs.get(i32()); const m = new Float32Array(9); for (let k = 0; k < 9; k++) m[k] = f32(); gl.uniformMatrix3fv(l, false, m); break; }
      case Op.UNIFORM_MATRIX_4: { const l = locs.get(i32()); const m = new Float32Array(16); for (let k = 0; k < 16; k++) m[k] = f32(); gl.uniformMatrix4fv(l, false, m); break; }
      case Op.ENABLE: gl.enable(i32()); break;
      case Op.DISABLE: gl.disable(i32()); break;
      case Op.DEPTH_FUNC: gl.depthFunc(i32()); break;
      case Op.DEPTH_MASK: gl.depthMask(i32() !== 0); break;
      case Op.BLEND_FUNC: { const s = i32(), d = i32(); gl.blendFunc(s, d); break; }
      case Op.CULL_FACE: gl.cullFace(i32()); break;
      case Op.VIEWPORT: { const x = i32(), y = i32(), w = i32(), h = i32(); gl.viewport(x, y, w, h); break; }
      case Op.CLEAR_COLOR: { const r = f32(), g = f32(), b = f32(), a = f32(); gl.clearColor(r, g, b, a); break; }
      case Op.CLEAR: { const m = i32(); if (capturing) gl.clear(m); break; }
      case Op.DRAW_ARRAYS: {
        const mode = i32(), first = i32(), count = i32();
        if (capturing) { gl.drawArrays(mode, first, count); draws++; }
        break;
      }
      case Op.DRAW_ELEMENTS: {
        const mode = i32(), count = i32(), type = i32(), off = i32();
        if (capturing) { gl.drawElements(mode, count, type, off); draws++; }
        break;
      }
      case Op.GEN_TEXTURE: { const id = i32(); objs.set(id, gl.createTexture()); break; }
      case Op.DELETE_TEXTURE: { const id = i32(); gl.deleteTexture(objs.get(id)); break; }
      case Op.ACTIVE_TEXTURE: gl.activeTexture(i32()); break;
      case Op.BIND_TEXTURE: { const t = i32(); gl.bindTexture(t, get(i32())); break; }
      case Op.TEX_PARAMETER: { const t = i32(), pn = i32(), v = i32(); gl.texParameteri(t, pn, v); break; }
      case Op.TEX_IMAGE_2D: {
        const t = i32(), lvl = i32(), ifmt = i32(), w = i32(), h = i32(), fmt = i32(), type = i32(), has = i32();
        if (has) gl.texImage2D(t, lvl, ifmt, w, h, 0, fmt, type, blob()); else gl.texImage2D(t, lvl, ifmt, w, h, 0, fmt, type, null);
        break;
      }
      case Op.GENERATE_MIPMAP: gl.generateMipmap(i32()); break;
      case Op.FRAME_END: {
        if (capturing) {
          const err = gl.getError();
          if (err !== 0) errors.push('GL error 0x' + err.toString(16) + ' in frame ' + frame);
          shots.push({ frame, png: canvas.toDataURL('image/png') });
        }
        frame++;
        capturing = want.has(frame);
        break;
      }
      default: throw new Error('unknown opcode ' + op + ' at byte ' + (p - 1));
    }
  }
  return { width, height, frames: frame, draws, errors, shots };
};
