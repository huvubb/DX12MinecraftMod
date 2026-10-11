#pragma once

#include <array>
#include <cstdint>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

struct CapturedMesh {
    std::shared_ptr<const std::vector<uint8_t>> vertices;
    std::vector<uint8_t> indices;
    std::string layout; // offset:usage:index:type:componentCount; for every vertex element
    int vertexCount = 0, stride = 0, indexCount = 0, indexBytes = 0, mode = 0;
    bool sequential = false; // Minecraft generates these indices from mode + vertexCount.
};

struct CapturedDraw {
    int64_t meshId;
    std::array<float, 35> matricesAndOffset; // column-major model-view, projection, then chunk xyz
    std::array<int32_t, 12> textures; // OpenGL texture handles; pixels are not copied yet.
    std::string shader;
    std::shared_ptr<const CapturedMesh> mesh; // Preserve this upload if the VBO is reused later in the frame.
};

// CPU-owned snapshots survive BufferBuilder.release(); GPU consumption is a later stage.
class RenderCapture {
public:
    std::unordered_map<int64_t, std::shared_ptr<const CapturedMesh>> meshes;
    std::vector<CapturedDraw> draws;
    int64_t uploads = 0, skippedUpdates = 0;

    void upload(int64_t id, CapturedMesh mesh, bool indexOnly) {
        if (id <= 0 || mesh.vertexCount < 0 || mesh.indexCount < 0 || mesh.stride <= 0 ||
                (mesh.indexBytes != 1 && mesh.indexBytes != 2 && mesh.indexBytes != 4) ||
                mesh.mode < 0 || mesh.mode > 7 || mesh.layout.empty()) {
            throw std::invalid_argument("Invalid mesh metadata");
        }
        const size_t vertexBytes = size_t(mesh.vertexCount) * size_t(mesh.stride);
        const size_t indexBytes = mesh.sequential ? 0 : size_t(mesh.indexCount) * size_t(mesh.indexBytes);
        if ((!indexOnly && (!mesh.vertices || mesh.vertices->size() != vertexBytes)) || mesh.indices.size() != indexBytes) {
            throw std::invalid_argument("Mesh buffer size does not match metadata");
        }
        auto old = meshes.find(id);
        if (indexOnly) {
            // Minecraft keeps the existing VBO bytes during transparency sorting.
            // DrawState.vertexCount describes the sort task, not a new vertex upload.
            if (old == meshes.end() || old->second->vertices->size() % size_t(mesh.stride) != 0) {
                release(id);
                ++skippedUpdates;
                return;
            }
            mesh.vertices = old->second->vertices;
            mesh.vertexCount = int(mesh.vertices->size() / size_t(mesh.stride));
        }
        for (int i = 0; !mesh.sequential && i < mesh.indexCount; ++i) {
            uint32_t index = 0;
            std::memcpy(&index, mesh.indices.data() + size_t(i) * mesh.indexBytes, mesh.indexBytes);
            if (index >= uint32_t(mesh.vertexCount)) {
                if (indexOnly) {
                    release(id);
                    ++skippedUpdates;
                    return;
                }
                throw std::invalid_argument("Index outside vertex buffer");
            }
        }
        meshes.insert_or_assign(id, std::make_shared<const CapturedMesh>(std::move(mesh)));
        ++uploads;
    }

    void draw(CapturedDraw draw) {
        auto mesh = meshes.find(draw.meshId);
        if (mesh != meshes.end() && mesh->second->indexCount > 0) {
            draw.mesh = mesh->second;
            draws.push_back(std::move(draw));
        }
    }

    void release(int64_t id) {
        meshes.erase(id);
        // Current frame draws keep their immutable geometry until the next beginFrame.
    }

    std::array<int64_t, 6> stats() const {
        std::array<int64_t, 6> result = {int64_t(meshes.size()), 0, 0, int64_t(draws.size()), uploads, skippedUpdates};
        for (const auto& entry : meshes) {
            result[1] += int64_t(entry.second->vertices->size());
            result[2] += int64_t(entry.second->indices.size());
        }
        return result;
    }
};
