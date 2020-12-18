package com.youzack.opencv;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

public class ReplaceGreenScreenFilter {
	
	private byte _greenScale = 1;
	private double _minBlockPercent = 0.01;
	private Mat _backgroundImage;

    public void setBackgroundImage(Mat backgroundImage)
    {
    	
        this._backgroundImage = backgroundImage;
    }
    
    public ReplaceGreenScreenFilter(Mat backgroundImage)
    {
        setBackgroundImage(backgroundImage);
    }
    
    /**
      * ��srcͼƬ������ص����¹��򿽱���matMast�У���ɫ�Ĳ���Ū�ɺ�ɫ������Ϊ��ɫ��
     * @param src
     * @param matMask
     * @throws Exception
     */
    
    private void renderGreenScreenMask(Mat src, Mat matMask) throws Exception
    {
        if (src.channels() < 3)
        {
            throw new IllegalArgumentException("src.Channels() should >=3");
        }
        if (matMask.channels() != 1)
        {
            throw new IllegalArgumentException("matMask.Channels() should == 1");
        }
        if (!Utils.equals(src.size(), matMask.size()))
        {
            throw new IllegalArgumentException("src.Size()!=matMask.Size()");
        }
        //https://codereview.stackexchange.com/questions/184044/processing-an-image-to-extract-green-screen-mask
        int rows = src.rows();
        int cols = src.cols();
        try(ResourceTracker t = new ResourceTracker())
        {
            UByteIndexer indexerSrc = t.T((UByteIndexer)src.createIndexer());//UByteRawIndexer        
            UByteIndexer indexerMask = t.T((UByteIndexer)matMask.createIndexer());//UByteRawIndexer
            
            for (int x = 0; x < rows; x++)
            {        	
                for (int y = 0; y < cols; y++)
                {
                    int blue = indexerSrc.get(x,y,0);
                    int green = indexerSrc.get(x,y,1);
                    int red = indexerSrc.get(x,y,2);
                    int max1 = blue > green ? blue : green;
                    int max = max1>red?max1:red;
                    //if this pixel is some green, render the pixel with the same position on matMask as black
                    if (green == max && green > this._greenScale)//�Ƿ��е���
                    {
                    	indexerMask.put(x, y,0);
                    }
                    else
                    {
                    	indexerMask.put(x, y,255);
                    }
                }
            }        	
        }
    }
    
    /**
     * ��alpha���������Ϊalphaͨ����͸������src�ϲ���dst��
     * @param src
     * @param dst
     * @param alpha
     * @throws Exception
     */
    public static void addAlphaChannel(Mat src, Mat dst, Mat alpha) throws Exception
	{
		try (ResourceTracker t = new ResourceTracker())
		{
			MatVector bgr = t.T(new MatVector());
			split(src,bgr);
			//split is used for splitting the channels separately
			MatVector bgra = t.T(new MatVector());
			bgra.put(bgr.get(0),bgr.get(1),bgr.get(2),alpha);
			merge(bgra, dst);
		}
	}
    
    /**
     * ��overlay�������͸����ͼƬ���Ƶ�bg���ͼƬ��
     * @param bg
     * @param overlay
     * @throws Exception
     */
    public static void drawOverlay(Mat bg, Mat overlay) throws Exception
	{
		if (!Utils.equals(bg.size(),overlay.size()))
		{
			throw new IllegalArgumentException("bg.Size()!=overlay.Size()");
		}
		if (overlay.channels() < 4)
		{
			throw new IllegalArgumentException("overlay.Channels()<4");
		}
		int colsOverlay = overlay.cols();
		int rowsOverlay = overlay.rows();
		 try(ResourceTracker t = new ResourceTracker())
		 {
			 UByteIndexer indexerBg = t.T((UByteIndexer)bg.createIndexer());//UByteRawIndexer        
	         UByteIndexer indexerOverlay = t.T((UByteIndexer)overlay.createIndexer());//UByteRawIndexer
	         
			//https://stackoverflow.com/questions/54069766/overlaying-an-image-over-another-image-both-with-transparent-background-using-op
			for (int i = 0; i < rowsOverlay; i++)
			{
				for (int j = 0; j < colsOverlay; j++)
				{
					int item0Overlay = indexerOverlay.get(i,j,0);
					int item1Overlay = indexerOverlay.get(i,j,1);
					int item2Overlay = indexerOverlay.get(i,j,2);
					int item3Overlay = indexerOverlay.get(i,j,3);
					if (item3Overlay != 0)
					{
						indexerBg.put(i,j,0,item0Overlay);
						indexerBg.put(i,j,1,item1Overlay);
						indexerBg.put(i,j,2,item2Overlay);
					}
				}
			}			 
		 }

	}
    
    public void apply(Mat src) throws Exception
    {
    	try(ResourceTracker t = new ResourceTracker())
    	{    		
    		Size srcSize = src.size();
    		//����
            Mat matMask = t.newMat(srcSize, CV_8UC1, new Scalar(0));
            renderGreenScreenMask(src, matMask);
            
            //��С�����������
            int minBlockArea = (int)(srcSize.width() * srcSize.height() * this._minBlockPercent);
            Mat matHierarchy =t.newMat();
            
            MatVector  contours =t.T(new MatVector());
            //��matMask�в��������������������������contours��
            findContours(matMask, contours, matHierarchy, RETR_EXTERNAL, CHAIN_APPROX_NONE);
            
            //ֻȡ����contours��������һЩ
            MatVector contoursExternalForeground =t.T(new MatVector());
            Mat[] contoursMats = contours.get();
            for(Mat contourMat : contoursMats)
            {
            	double contourArea = contourArea(contourMat);
            	if(contourArea>minBlockArea)
            	{
            		contoursExternalForeground.put(contourMat);
            	}
            }
            
            //ǰ������
            Mat matMaskForeground = t.newMat(srcSize, CV_8UC1, new Scalar(0));
            //thickness: -1 means filling the inner space 
            //�Ѵ������ʶ������ġ����塱���Ƶ�matMaskForeground��
            drawContours(matMaskForeground,contoursExternalForeground,-1,new Scalar(255),-1,LINE_8,
            		null,Integer.MAX_VALUE,new Point(0,0));
            
            //������ġ��οա�����
            Mat matInternalHollow = t.newMat(srcSize, CV_8UC1, new Scalar(0));
            bitwise_xor(matMaskForeground, matMask, matInternalHollow);

            //�Ѵ������ʶ������ġ��������οա����Ƶ�matMaskForeground��
            int minHollowArea = (int)(minBlockArea * 0.01);//the lower size limitation of InternalHollow is less than minBlockArea, because InternalHollows are smaller
            //find the Contours of Internal Hollow 
            MatVector contoursInternalHollow =t.T(new MatVector());
            findContours(matInternalHollow,contoursInternalHollow,matHierarchy, RETR_EXTERNAL, CHAIN_APPROX_NONE);
            for(Mat contourMat : contoursInternalHollow.get())
            {
            	double contourArea = contourArea(contourMat);
            	if(contourArea>minHollowArea)
            	{
            		fillConvexPoly(matMaskForeground,contourMat,new Scalar(0));
            	}            	
            }
            
            Mat foreground = t.newMat(src.size(), CV_8UC4, new Scalar(0));
            addAlphaChannel(src, foreground, matMaskForeground);
            resize(_backgroundImage, src, src.size());
            //draw foreground(people) on the backgroundimage
            drawOverlay(src, foreground);
    	}
    }
}
