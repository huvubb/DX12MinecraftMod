#include "RenderCapture.h"
#include <cassert>

int main() {
    RenderCapture capture;
    CapturedMesh mesh;
    mesh.vertices = std::make_shared<const std::vector<uint8_t>>(16, 42);
    mesh.vertexCount = 4; mesh.stride = 4; mesh.indexCount = 6;
    mesh.indexBytes = 1; mesh.mode = 7; mesh.layout = "0:POSITION:0:BYTE:4;";
    mesh.indices = {0, 1, 2, 2, 3, 0};
    capture.upload(1, mesh, false);
    CapturedDraw draw{};
    draw.meshId = 1; draw.matricesAndOffset[32] = 16; draw.textures[0] = 7;
    capture.draw(draw);

    mesh.indices = {3, 2, 1, 1, 0, 3};
    mesh.vertices.reset();
    capture.upload(1, mesh, true);
    assert(capture.meshes.at(1)->vertices == capture.draws[0].mesh->vertices);
    assert(capture.meshes.at(1)->indices[0] == 3);
    assert(capture.draws[0].mesh->indices[0] == 0);
    assert(capture.draws[0].matricesAndOffset[32] == 16);
    assert(capture.draws[0].textures[0] == 7);

    mesh.vertices = std::make_shared<const std::vector<uint8_t>>(16, 99);
    capture.upload(1, mesh, false);
    assert(capture.draws[0].mesh->vertices->at(0) == 42);
    assert(capture.meshes.at(1)->vertices->at(0) == 99);
    capture.release(1);
    assert(capture.meshes.empty() && capture.draws[0].mesh->vertices->at(0) == 42);
    capture.draws.clear();
    assert(capture.stats()[3] == 0);

    capture.upload(1, mesh, false);
    mesh.vertices.reset();
    mesh.vertexCount = 3; // Sort metadata does not replace the cached four-vertex VBO.
    capture.upload(1, mesh, true);
    assert(capture.meshes.at(1)->vertexCount == 4);
    assert(capture.meshes.at(1)->vertices->size() == 16);
    capture.upload(2, mesh, true); // Orphaned index updates are expected during chunk changes.
    assert(capture.meshes.count(2) == 0 && capture.skippedUpdates == 1);
    mesh.indices[0] = 4;
    capture.upload(1, mesh, true);
    assert(capture.meshes.count(1) == 0 && capture.skippedUpdates == 2);
}
