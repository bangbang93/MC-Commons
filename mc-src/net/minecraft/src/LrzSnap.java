package net.minecraft.src;

import java.awt.image.BufferedImage;

import net.minecraft.client.Minecraft;

public class LrzSnap implements LrzSnapI
{
	private int coordA;
	private int coordB;
	private boolean changed;
	private BufferedImage image;
	
	private LrzWorldCacheI worldCache;
	private LrzMagI[][] mags;
	
	private int[][] gatherBuffer;
	
	private int[][] failedBuffer;
	
	public LrzSnap(LrzWorldCacheI worldCache, int snapCoordA, int snapCoordB)
	{
		this.worldCache = worldCache;
		this.coordA = snapCoordA;
		this.coordB = snapCoordB;
		this.changed = false;
		this.gatherBuffer = new int[this.worldCache.getSplit()][this.worldCache
		                                                        .getSplit()];
		
		buildEmptyImage();
		
		buildMags();
		
		buildFailedBuffer();
		
	}
	
	public LrzSnap(LrzWorldCacheI worldCache, int snapCoordA, int snapCoordB,
			BufferedImage bufferedImage)
	{
		this.worldCache = worldCache;
		this.coordA = snapCoordA;
		this.coordB = snapCoordB;
		this.changed = false;
		this.gatherBuffer = new int[this.worldCache.getSplit()][this.worldCache
		                                                        .getSplit()];
		
		this.image = bufferedImage; // FIXME: This assumes the image has the correct dimensions
		
		buildMags();
		
		buildFailedBuffer();
		
	}
	
	private void buildFailedBuffer()
	{
		int s = this.worldCache.getSplit();
		int m = s - 1;
		int v = 0xFFFFFF;
		this.failedBuffer = new int[s][s];
		
		for (int i = 0; i < s; i++)
		{
			this.failedBuffer[0][i] = v;
			this.failedBuffer[m][i] = v;
			this.failedBuffer[i][0] = v;
			this.failedBuffer[i][m] = v;
			
		}
		
		
	}
	private void buildEmptyImage()
	{
		int size = this.worldCache.getSideCount() * this.worldCache.getSplit();
		image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		
	}
	private void buildMags()
	{
		int sideCount = this.worldCache.getSideCount();
		mags = new LrzMag[sideCount][sideCount];
		for (int i = 0; i < sideCount; i++)
			for (int j = 0; j < sideCount; j++)
				mags[i][j] = new LrzMag(this);
		
	}
	
	private int modulus(int a, int n)
	{
		return (int) (a - n * Math.floor(((float) a) / n));
		
	}
	
	@Override
	public int requestAverage(int worldX, int worldZ)
	{
		int iMag = modulus((worldX / LrzWorldCache.CHUNK_SIZE), worldCache
				.getSideCount());
		int jMag = modulus((worldZ / LrzWorldCache.CHUNK_SIZE), worldCache
				.getSideCount());
		int split = worldCache.getSplit();
		
		int average = 0;
		
		LrzMagI mag = mags[iMag][jMag];
		
		int tick = worldCache.mod().corn().util().getClientTick();
		if (mag.hasTimeout(tick))
		{
			boolean gathered = gatherChunk(iMag, jMag);
			if (mag.isGathered())
				mag.setTimeout(tick + LrzWorldCache.TICKS_TO_CACHE_TIMEOUT);
			else
				mag.setTimeout(tick + LrzWorldCache.TICKS_TO_RETRY_TIMEOUT);
			
			if (gathered)
				mag.markGathered();
			
			changed = true;
			
		}
		
		if (mag.isGathered())
		{
			average = image.getRGB(iMag
					* split
					+ modulus((worldX / (LrzWorldCache.CHUNK_SIZE / split)),
							split), jMag
							* split
							+ modulus((worldZ / (LrzWorldCache.CHUNK_SIZE / split)),
							split)) & 0xFF;
			
		}
		else
		{
			average = 0;
			
		}
		
		return average;
	}
	
	private int sane(float a)
	{
		return (int) Math.floor(a);
		
	}
	
	private boolean gatherChunk(int iMag, int jMag)
	{
		int xOrigin = coordA * worldCache.getSideCount()
				* LrzWorldCache.CHUNK_SIZE + iMag * LrzWorldCache.CHUNK_SIZE;
		int zOrigin = coordB * worldCache.getSideCount()
				* LrzWorldCache.CHUNK_SIZE + jMag * LrzWorldCache.CHUNK_SIZE;
		int splits = worldCache.getSplit();
		int splitPhysicalSize = LrzWorldCache.CHUNK_SIZE / splits;
		int splitPhysicalSizeSquared = splitPhysicalSize * splitPhysicalSize;
		Minecraft mc = worldCache.mod().manager().getMinecraft();
		
		boolean fail = false;
		
		for (int b = 0; (b < splits) && (!fail); b++)
		{
			int zPush = b * splitPhysicalSize;
			
			for (int a = 0; (a < splits) && (!fail); a++)
			{
				int xPush = a * splitPhysicalSize;
				
				int average = 0;
				for (int xPand = 0; xPand < splitPhysicalSize; xPand++)
				{
					for (int zPand = 0; zPand < splitPhysicalSize; zPand++)
					{
						average = average
								+ mc.theWorld.getHeightValue(xOrigin + xPush
										+ xPand, zOrigin + zPush + zPand);
						
					}
				}
				average = average / splitPhysicalSizeSquared;
				if (average == 0)
				{
					fail = true;
				}
				int o = average;
				this.gatherBuffer[a][b] = o << 16 | o << 8 | o;
				
			}
		}
		if (!fail)
		{
			for (int b = 0; b < splits && (!fail); b++)
			{
				for (int a = 0; a < splits && (!fail); a++)
				{
					image.setRGB(iMag * splits + a, jMag * splits + b,
							this.gatherBuffer[a][b]);
					
				}
			}
			
		}
		else
		{
			for (int b = 0; b < splits && (!fail); b++)
			{
				for (int a = 0; a < splits && (!fail); a++)
				{
					image.setRGB(iMag * splits + a, jMag * splits + b,
							this.failedBuffer[a][b]);
					
				}
			}
			LrzMod.LOGGER.warning("FAILED with " + iMag + " " + jMag);
		}
		
		return !fail;
		
	}
	
	@Override
	public boolean hasChanged()
	{
		return changed;
	}
	
	@Override
	public void sendMeta(String metaString) throws LrzInvalidDataException
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public BufferedImage getImage()
	{
		return image;
	}
	
	@Override
	public String getMetaString()
	{
		// TODO Implement me
		return "";
	}
	
	@Override
	public int getCoordA()
	{
		return coordA;
	}
	
	@Override
	public int getCoordB()
	{
		return coordB;
	}
	
	@Override
	public void clearChangeState()
	{
		changed = false;
		
	}
	
}
