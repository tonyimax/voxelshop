package com.vitco.engine;

import com.threed.jpct.Camera;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.World;
import com.vitco.engine.data.container.Voxel;
import com.vitco.res.VitcoSettings;
import com.vitco.util.WorldUtil;

import java.awt.*;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * This is a world wrapper that provides easy voxel interaction.
 */
public class CWorld extends World {
    private static final long serialVersionUID = 1L;

    // constructor
    private final boolean culling;
    private final Integer side; // default -1 (all sites)
    public CWorld(boolean culling, Integer side) {
        this.culling = culling;
        this.side = side;
    }

    // ===========================
    // tight logic
    // ===========================

    // holds the voxel wrapper positions
    private final HashMap<String, VoxelW> voxelPos = new HashMap<String, VoxelW>();

    // voxel that need to be redrawn
    private final HashSet<VoxelW> toUpdate = new HashSet<VoxelW>();

    // Wrapper object that holds voxel information used by CWorld
    private static final class VoxelW implements Serializable {
        private static final long serialVersionUID = 1L;

        // holds the current wrapped voxel object
        private Voxel voxel;
        // holds the neighbor information (different sides)
        private final VoxelW[] neighbours = new VoxelW[6];

        // holds the voxel wrapper positions
        private final HashMap<String, VoxelW> voxelPos;

        // voxel that need to be redrawn
        private final HashSet<VoxelW> toUpdate;

        public VoxelW(Voxel voxel, HashMap<String, VoxelW> voxelPos, HashSet<VoxelW> toUpdate) {
            this.voxel = voxel;
            // find neighbours and update side information (of them and this)
            // calculate the required sides
            for (int i = 0; i < 6; i++) {
                int add = i%2 == 0 ? 1 : -1;
                neighbours[i] = voxelPos.get(
                        (i/2 == 0 ? voxel.x + add : voxel.x) + "_" +
                                (i/2 == 1 ? voxel.y + add : voxel.y) + "_" +
                                (i/2 == 2 ? voxel.z + add : voxel.z)
                );
                if (neighbours[i] != null) {
                    neighbours[i].addNeighbour(this, i%2 == 0?i+1:i-1);
                }
            }
            this.voxelPos = voxelPos;
            this.toUpdate = toUpdate;
            // add this voxel to the position info
            voxelPos.put(getPos(), this);
            toUpdate.add(this);
        }

        // the voxel might be the same that is already stored
        public void refresh(Voxel voxel) {
            // note: this might be the same voxel,
            // e.g. this.voxel == voxel can happen (in that case we still need to update!)
            this.voxel = voxel;
            toUpdate.add(this);
        }

        public void addNeighbour(VoxelW neighbour, int side) {
            assert neighbours[side] == null;
            neighbours[side] = neighbour;
            toUpdate.add(this);
        }

        public void removeNeighbour(int side) {
            assert neighbours[side] != null;
            neighbours[side] = null;
            toUpdate.add(this);
        }

        // ========================
        // remove/replace voxel

        // returns true iff this wrapper was not removed
        private boolean removed = false;
        public boolean notRemoved() {
            return !removed;
        }

        // remove this voxel
        public void remove() {
            // update side information of neighbours
            for (int i = 0; i < 6; i++) {
                if (neighbours[i] != null) {
                    neighbours[i].removeNeighbour(i%2 == 0?i+1:i-1);
                }
            }
            // remove this voxel from the position info
            voxelPos.remove(getPos());
            toUpdate.add(this);
            // update removed information
            removed = true;
        }

        // ========================
        // basic information

        // get this position information as a string
        public String getPos() {
            return voxel.getPosAsString();
        }

        // get the side information of this voxel
        public String getSides() {
            StringBuilder sides = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sides.append(neighbours[i] == null ? "0" : "1");
            }
            return sides.toString();
        }

        // =======================
        // world id information
        private Integer worldId = null;

        public Integer getWorldId() {
            return worldId;
        }

        public void setWorldId(Integer worldId) {
            this.worldId = worldId;
        }

        // get underlying voxel information
        public Color getColor() {
            return voxel.getColor();
        }
        public int[] getRotation() {
            return voxel.getRotation();
        }
        public boolean[] getFlip() {
            return voxel.getFlip();
        }
        public int[] getTexture() {
            return voxel.getTexture();
        }
        public SimpleVector getVectorPos() {
            return new SimpleVector(
                    voxel.x * VitcoSettings.VOXEL_SIZE,
                    voxel.y * VitcoSettings.VOXEL_SIZE,
                    voxel.z * VitcoSettings.VOXEL_SIZE);
        }
        public int getVoxelId() {
            return voxel.id;
        }
        public Voxel getVoxel() {
            return voxel;
        }
    }

    // ==============================
    // drawing of selected (wireframe)

    // move offset
    private SimpleVector offset = new SimpleVector(0, 0, 0);
    private float length = offset.length();

    // set the shift of this (just used for "drawAsShiftedWireframe"
    public final void setShift(int[] shift) {
        offset = new SimpleVector(
                shift[0],
                shift[1],
                shift[2]);
        length = offset.length() * VitcoSettings.VOXEL_SIZE;
        offset = offset.normalize();
    }

    // draw just the wireframe (possible shifted)
    public final void drawAsShiftedWireframe(FrameBuffer buffer, Color selected, Color shifted) {
        if (length != 0) {
            getCamera().moveCamera(offset, length);
            renderScene(buffer);
            drawWireframe(buffer, shifted);
            getCamera().moveCamera(offset, -length);
        } else {
            renderScene(buffer);
            drawWireframe(buffer, selected);
        }
    }

    public final SimpleVector shiftedCollisionPoint(SimpleVector dir) {
        // check if we hit a <selected> voxel
        SimpleVector result = null;
        Camera camera = getCamera();
        camera.moveCamera(offset, length);
        Object[] res = calcMinDistanceAndObject3D(camera.getPosition(), dir, 100000);
        if (res[1] != null) { // something hit
            // find collision point
            result = camera.getPosition();
            dir.scalarMul((Float)res[0]);
            result.add(dir);
        }
        camera.moveCamera(offset, -length);
        return result;
    }

    // add voxel
    public final void updateVoxel(Voxel voxel) {
        String pos = voxel.getPosAsString();
        if (voxelPos.containsKey(pos)) {
            voxelPos.get(pos).refresh(voxel); // update voxel
        } else {
            new VoxelW(voxel, voxelPos, toUpdate); // add new voxel
        }
    }

    public final void clear() {
        for (Object voxel : voxelPos.values().toArray()) {
            ((VoxelW)voxel).remove();
        }
    }

    // clear field by position
    public final boolean clearPosition(int[] pos) {
        boolean result = false;
        VoxelW wrapper = voxelPos.get(pos[0] + "_" + pos[1] + "_" + pos[2]);
        if (wrapper != null) {
            wrapper.remove();
            result = true;
        }
        return result;
    }

    public final boolean clearPosition(Voxel voxel) {
        boolean result = false;
        VoxelW wrapper = voxelPos.get(voxel.getPosAsString());
        if (wrapper != null) {
            wrapper.remove();
            result = true;
        }
        return result;
    }

    // refresh world partially - returns true if fully refreshed
    public final boolean refreshWorld() {
        int count = 200;
        for (Iterator<VoxelW> it = toUpdate.iterator(); it.hasNext() && count-- > 0;) {
            VoxelW voxel = it.next();
            Integer worldId = voxel.getWorldId();
            if (worldId != null) {
                // remove current representation in world
               removeObject(worldId);
               worldIdToVoxelId.remove(worldId);
            }
            if (voxel.notRemoved()) {
                String sides;
                if (side == -1) {
                    sides = voxel.getSides();
                } else {
                    sides = side == 0 ? "111110" : (side == 1 ? "111011" : "101111");
                }
                // add this (updated) voxel to the world
                int newWorldId = WorldUtil.addBoxSides(this,
                        voxel.getVectorPos(),
                        voxel.getColor(),
                        voxel.getRotation(),
                        voxel.getFlip(),
                        voxel.getTexture(),
                        // draw the appropriate site only
                        sides,
                        culling,
                        side == -1);
                // remember the world id
                voxel.setWorldId(newWorldId);
                // remember the mapping
                worldIdToVoxelId.put(newWorldId, voxel.getVoxelId());
            }
            it.remove();
        }
        return toUpdate.isEmpty();
    }

    // maps world ids to voxel ids
    private final HashMap<Integer, Integer> worldIdToVoxelId = new HashMap<Integer, Integer>();

    // retrieve voxel for object id
    public final Integer getVoxelId(Integer objectId) {
        return worldIdToVoxelId.get(objectId);
    }

    // retrieve all visible voxels in this world
    public HashMap<Voxel, String> getVisibleVoxel() {
        HashMap<Voxel, String> result = new HashMap<Voxel, String>();
        for (VoxelW voxel : voxelPos.values()) {
            String sides = voxel.getSides();
            if (!sides.equals("111111")) {
                result.put(voxel.getVoxel(), voxel.getSides());
            }
        }
        return result;
    }

}