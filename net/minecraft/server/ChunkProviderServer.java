package net.minecraft.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ChunkProviderServer implements IChunkProvider {

    private Set unloadQueue = new HashSet();
    private Chunk emptyChunk;
    private IChunkProvider chunkProvider;
    private IChunkLoader e;
    public boolean forceChunkLoad = true;
    private LongHashMap chunks = new LongHashMap();
    private List chunkList = new ArrayList();
    private WorldServer world;

    public ChunkProviderServer(WorldServer worldserver, IChunkLoader ichunkloader, IChunkProvider ichunkprovider) {
        this.emptyChunk = new EmptyChunk(worldserver, 0, 0);
        this.world = worldserver;
        this.e = ichunkloader;
        this.chunkProvider = ichunkprovider;
    }

    public boolean isChunkLoaded(int i, int j) {
        return this.chunks.contains(ChunkCoordIntPair.a(i, j));
    }

    public void queueUnload(int i, int j) {
        if (this.world.worldProvider.e()) {
            ChunkCoordinates chunkcoordinates = this.world.getSpawn();
            int k = i * 16 + 8 - chunkcoordinates.x;
            int l = j * 16 + 8 - chunkcoordinates.z;
            short short1 = 128;

            if (k < -short1 || k > short1 || l < -short1 || l > short1) {
                this.unloadQueue.add(Long.valueOf(ChunkCoordIntPair.a(i, j)));
            }
        } else {
            this.unloadQueue.add(Long.valueOf(ChunkCoordIntPair.a(i, j)));
        }
    }

    public void a() {
        Iterator iterator = this.chunkList.iterator();

        while (iterator.hasNext()) {
            Chunk chunk = (Chunk) iterator.next();

            this.queueUnload(chunk.x, chunk.z);
        }
    }

    public Chunk getChunkAt(int i, int j) {
        long k = ChunkCoordIntPair.a(i, j);

        this.unloadQueue.remove(Long.valueOf(k));
        Chunk chunk = (Chunk) this.chunks.getEntry(k);

        if (chunk == null) {
            chunk = this.loadChunk(i, j);
            if (chunk == null) {
                if (this.chunkProvider == null) {
                    chunk = this.emptyChunk;
                } else {
                    try {
                        chunk = this.chunkProvider.getOrCreateChunk(i, j);
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.a(throwable, "Exception generating new chunk");
                        CrashReportSystemDetails crashreportsystemdetails = crashreport.a("Chunk to be generated");

                        crashreportsystemdetails.a("Location", String.format("%d,%d", new Object[] { Integer.valueOf(i), Integer.valueOf(j)}));
                        crashreportsystemdetails.a("Position hash", Long.valueOf(k));
                        crashreportsystemdetails.a("Generator", this.chunkProvider.getName());
                        throw new ReportedException(crashreport);
                    }
                }
            }

            this.chunks.put(k, chunk);
            this.chunkList.add(chunk);
            if (chunk != null) {
                chunk.addEntities();
            }

            chunk.a(this, this, i, j);
        }

        return chunk;
    }

    public Chunk getOrCreateChunk(int i, int j) {
        Chunk chunk = (Chunk) this.chunks.getEntry(ChunkCoordIntPair.a(i, j));

        return chunk == null ? (!this.world.isLoading && !this.forceChunkLoad ? this.emptyChunk : this.getChunkAt(i, j)) : chunk;
    }

    private Chunk loadChunk(int i, int j) {
        if (this.e == null) {
            return null;
        } else {
            try {
                Chunk chunk = this.e.a(this.world, i, j);

                if (chunk != null) {
                    chunk.n = this.world.getTime();
                    if (this.chunkProvider != null) {
                        this.chunkProvider.recreateStructures(i, j);
                    }
                }

                return chunk;
            } catch (Exception exception) {
                exception.printStackTrace();
                return null;
            }
        }
    }

    private void saveChunkNOP(Chunk chunk) {
        if (this.e != null) {
            try {
                this.e.b(this.world, chunk);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private void saveChunk(Chunk chunk) {
        if (this.e != null) {
            try {
                chunk.n = this.world.getTime();
                this.e.a(this.world, chunk);
            } catch (IOException ioexception) {
                ioexception.printStackTrace();
            } catch (ExceptionWorldConflict exceptionworldconflict) {
                exceptionworldconflict.printStackTrace();
            }
        }
    }

    public void getChunkAt(IChunkProvider ichunkprovider, int i, int j) {
        Chunk chunk = this.getOrCreateChunk(i, j);

        if (!chunk.done) {
            chunk.done = true;
            if (this.chunkProvider != null) {
                this.chunkProvider.getChunkAt(ichunkprovider, i, j);
                chunk.e();
            }
        }
    }

    public boolean saveChunks(boolean flag, IProgressUpdate iprogressupdate) {
        int i = 0;

        for (int j = 0; j < this.chunkList.size(); ++j) {
            Chunk chunk = (Chunk) this.chunkList.get(j);

            if (flag) {
                this.saveChunkNOP(chunk);
            }

            if (chunk.a(flag)) {
                this.saveChunk(chunk);
                chunk.l = false;
                ++i;
                if (i == 24 && !flag) {
                    return false;
                }
            }
        }

        return true;
    }

    public void b() {
        if (this.e != null) {
            this.e.b();
        }
    }

    public boolean unloadChunks() {
        if (!this.world.savingDisabled) {
            for (int i = 0; i < 100; ++i) {
                if (!this.unloadQueue.isEmpty()) {
                    Long olong = (Long) this.unloadQueue.iterator().next();
                    Chunk chunk = (Chunk) this.chunks.getEntry(olong.longValue());

                    chunk.removeEntities();
                    this.saveChunk(chunk);
                    this.saveChunkNOP(chunk);
                    this.unloadQueue.remove(olong);
                    this.chunks.remove(olong.longValue());
                    this.chunkList.remove(chunk);
                }
            }

            if (this.e != null) {
                this.e.a();
            }
        }

        return this.chunkProvider.unloadChunks();
    }

    public boolean canSave() {
        return !this.world.savingDisabled;
    }

    public String getName() {
        return "ServerChunkCache: " + this.chunks.count() + " Drop: " + this.unloadQueue.size();
    }

    public List getMobsFor(EnumCreatureType enumcreaturetype, int i, int j, int k) {
        return this.chunkProvider.getMobsFor(enumcreaturetype, i, j, k);
    }

    public ChunkPosition findNearestMapFeature(World world, String s, int i, int j, int k) {
        return this.chunkProvider.findNearestMapFeature(world, s, i, j, k);
    }

    public int getLoadedChunks() {
        return this.chunks.count();
    }

    public void recreateStructures(int i, int j) {}
}
