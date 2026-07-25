/**
 * 优化 world-cn.json 访客地图 GeoJSON：
 * 1. 属性精简：仅保留 name（+ 中国省份的 level='province'）
 * 2. 坐标降精度：round 到 3 位小数（约 110m，省/国家级热力图足够）
 * 3. 顶点去重：移除降精度后产生的连续重复点，丢弃退化环（<4 点）
 * 4. Douglas-Peucker 抽稀：容差 0.005 度（约 550m），省/国家级视图无感知
 */
const fs = require('fs');
const zlib = require('zlib');

const SRC = 'd:/Awesome-poetize-open/poetize-admin/public/map/world-cn.json';
const PRECISION = 3;
// 差异化容差：中国省份保留细节（支持放大查看），国家轮廓可更粗
const DP_TOLERANCE_PROVINCE = 0.005; // 约 550m
const DP_TOLERANCE_COUNTRY = 0.02;   // 约 2.2km

// Douglas-Peucker 折线抽稀（迭代实现，避免深递归爆栈）
function douglasPeucker(points, tolerance) {
  if (points.length <= 4) return points;
  const keep = new Array(points.length).fill(false);
  keep[0] = keep[points.length - 1] = true;
  const stack = [[0, points.length - 1]];
  while (stack.length) {
    const [start, end] = stack.pop();
    let maxDist = 0;
    let index = -1;
    const [x1, y1] = points[start];
    const [x2, y2] = points[end];
    const dx = x2 - x1;
    const dy = y2 - y1;
    const norm = Math.sqrt(dx * dx + dy * dy);
    for (let i = start + 1; i < end; i++) {
      const [px, py] = points[i];
      // 点到线段的垂直距离（退化为点时用欧氏距离）
      const dist = norm === 0
        ? Math.sqrt((px - x1) ** 2 + (py - y1) ** 2)
        : Math.abs(dy * px - dx * py + x2 * y1 - y2 * x1) / norm;
      if (dist > maxDist) {
        maxDist = dist;
        index = i;
      }
    }
    if (maxDist > tolerance && index > 0) {
      keep[index] = true;
      stack.push([start, index], [index, end]);
    }
  }
  return points.filter((_, i) => keep[i]);
}

const round = (n) => {
  const p = Math.pow(10, PRECISION);
  return Math.round(n * p) / p;
};

// 处理一个环（坐标点数组）：DP抽稀 + 降精度 + 连续重复点去重
function simplifyRing(ring, tolerance) {
  const thinned = douglasPeucker(ring, tolerance);
  const out = [];
  for (const pt of thinned) {
    const x = round(pt[0]);
    const y = round(pt[1]);
    const last = out[out.length - 1];
    if (!last || last[0] !== x || last[1] !== y) {
      out.push([x, y]);
    }
  }
  // 闭合环：首尾必须一致
  if (out.length > 1) {
    const first = out[0];
    const last = out[out.length - 1];
    if (first[0] !== last[0] || first[1] !== last[1]) {
      out.push([first[0], first[1]]);
    }
  }
  return out;
}

function simplifyGeometry(geom, tolerance) {
  if (!geom) return null;
  if (geom.type === 'Polygon') {
    const rings = geom.coordinates.map(r => simplifyRing(r, tolerance)).filter(r => r.length >= 4);
    return rings.length ? { type: 'Polygon', coordinates: rings } : null;
  }
  if (geom.type === 'MultiPolygon') {
    const polys = geom.coordinates
      .map(poly => poly.map(r => simplifyRing(r, tolerance)).filter(r => r.length >= 4))
      .filter(poly => poly.length);
    return polys.length ? { type: 'MultiPolygon', coordinates: polys } : null;
  }
  return geom;
}

const geo = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const before = fs.statSync(SRC).size;
let droppedFeatures = 0;
let totalPointsBefore = 0;
let totalPointsAfter = 0;

const countPoints = (geom) => {
  if (!geom) return 0;
  let n = 0;
  const walk = (arr) => {
    if (typeof arr[0] === 'number') { n++; return; }
    arr.forEach(walk);
  };
  walk(geom.coordinates);
  return n;
};

const features = [];
for (const f of geo.features) {
  totalPointsBefore += countPoints(f.geometry);
  const isProvince = f.properties.level === 'province';
  const geometry = simplifyGeometry(f.geometry, isProvince ? DP_TOLERANCE_PROVINCE : DP_TOLERANCE_COUNTRY);
  if (!geometry) { droppedFeatures++; continue; }
  totalPointsAfter += countPoints(geometry);
  const props = { name: f.properties.name };
  if (isProvince) props.level = 'province';
  features.push({ type: 'Feature', properties: props, geometry });
}

const out = JSON.stringify({ type: 'FeatureCollection', features });
fs.writeFileSync(SRC, out);

const after = Buffer.byteLength(out);
const gzipped = zlib.gzipSync(out, { level: 6 }).length;
console.log(`features: ${geo.features.length} -> ${features.length} (dropped ${droppedFeatures} degenerate)`);
console.log(`vertices: ${totalPointsBefore} -> ${totalPointsAfter} (dedup ${(100 - totalPointsAfter * 100 / totalPointsBefore).toFixed(1)}%)`);
console.log(`raw size: ${(before / 1024).toFixed(0)}KB -> ${(after / 1024).toFixed(0)}KB`);
console.log(`gzip(level6) transfer size: ${(gzipped / 1024).toFixed(0)}KB`);
