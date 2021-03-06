package ch.unibas.cs.dbis.cineast.core.segmenter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ch.unibas.cs.dbis.cineast.core.data.Frame;
import ch.unibas.cs.dbis.cineast.core.data.Histogram;
import ch.unibas.cs.dbis.cineast.core.data.Shot;
import ch.unibas.cs.dbis.cineast.core.data.providers.ShotProvider;
import ch.unibas.cs.dbis.cineast.core.db.PersistencyWriter;
import ch.unibas.cs.dbis.cineast.core.db.PersistentTuple;
import ch.unibas.cs.dbis.cineast.core.db.ShotLookup.ShotDescriptor;
import ch.unibas.cs.dbis.cineast.core.decode.subtitle.SubTitle;
import ch.unibas.cs.dbis.cineast.core.decode.subtitle.SubtitleItem;
import ch.unibas.cs.dbis.cineast.core.decode.video.VideoDecoder;

public class ShotSegmenter implements ShotProvider{
	
	private static final double THRESHOLD = 0.05;
	private static final int PRE_QUEUE_LEN = 10;
	private static final int MAX_SHOT_LENGTH = 720;

	private VideoDecoder vdecoder;
	private final long movieId;
	private LinkedList<Frame> frameQueue = new LinkedList<>();
	private LinkedList<DoublePair<Frame>> preShotQueue = new LinkedList<>();
	private ArrayList<SubTitle> subtitles = new ArrayList<SubTitle>();
	@SuppressWarnings("rawtypes")
	private PersistencyWriter pwriter;
	private List<ShotDescriptor> knownShotBoundaries;
	
	public ShotSegmenter(VideoDecoder vdecoder, long movieId, @SuppressWarnings("rawtypes") PersistencyWriter pwriter, List<ShotDescriptor> knownShotBoundaries){
		this.vdecoder = vdecoder;
		this.movieId = movieId;
		this.pwriter = pwriter;
		this.pwriter.open("cineast.shots");
		this.knownShotBoundaries = ((knownShotBoundaries == null) ? new LinkedList<ShotDescriptor>() : knownShotBoundaries);
	}
	
	public void addSubTitle(SubTitle st) {
		this.subtitles.add(st);
	}
	
	private boolean queueFrames(){
		return queueFrames(20);
	}
	
	private boolean queueFrames(int number){
		Frame f;
		for(int i = 0; i < number; ++i){
			f = this.vdecoder.getFrame();
			if(f == null){ //no more frames
				return false;
			}else{
				this.frameQueue.offer(f);
			}
		}
		return true;
	}
	
	
	
	public Shot getNextShot(){
		if(this.frameQueue.isEmpty()){
			queueFrames();
		}
		
		Shot _return = null;
		
		if (!preShotQueue.isEmpty()){
			_return = new Shot(this.movieId, this.vdecoder.getTotalFrameCount());
			while (!preShotQueue.isEmpty()) {
				_return.addFrame(preShotQueue.removeFirst().first);
			}
		}
		if(this.frameQueue.isEmpty()){
			return finishShot(_return); //no more shots to segment
		}
		
		if(_return == null){
			_return = new Shot(this.movieId, this.vdecoder.getTotalFrameCount());
		}
		
		
		Frame frame = this.frameQueue.poll();
		
		ShotDescriptor bounds = this.knownShotBoundaries.size() > 0 ? this.knownShotBoundaries.remove(0) : null;
		
		if (bounds != null && frame.getId() >= bounds.getStartFrame() && frame.getId() <= bounds.getEndFrame()){
			
			_return.addFrame(frame);
			queueFrames(bounds.getEndFrame() - bounds.getStartFrame());
			do{
				frame = this.frameQueue.poll();
				if(frame != null){
					_return.addFrame(frame);
				}else{
					break;
				}
				
			}while(frame.getId() < bounds.getEndFrame());
			
			_return.setShotId(bounds.getShotId());
			addSubtitleItems(_return);
			
			
			return _return;
			
		}else{
			Histogram hPrev, h = getHistogram(frame);
			_return.addFrame(frame);
			while (true) {
				if ((frame = this.frameQueue.poll()) == null) {
					queueFrames();
					if ((frame = this.frameQueue.poll()) == null) {
						return finishShot(_return);
					}
				}
				hPrev = h;
				h = getHistogram(frame);
				double distance = hPrev.getDistance(h);

				preShotQueue.offer(new DoublePair<Frame>(frame, distance));

				if (preShotQueue.size() > PRE_QUEUE_LEN) {
					double max = 0;
					int index = -1, i = 0;
					for (DoublePair<Frame> pair : preShotQueue) {
						if (pair.second > max) {
							index = i;
							max = pair.second;
						}
						i++;
					}
					if (max <= THRESHOLD && _return.getNumberOfFrames() < MAX_SHOT_LENGTH) { //no cut
						for (DoublePair<Frame> pair : preShotQueue) {
							_return.addFrame(pair.first);
						}
						preShotQueue.clear();
					} else {
						for (i = 0; i < index; ++i) {
							_return.addFrame(preShotQueue.removeFirst().first);
						}
						break;
					}
				}
			}
			return finishShot(_return);
		}
	}
	
	private static Histogram getHistogram(Frame f){
		return FuzzyColorHistogramCalculator.getSubdividedHistogramNormalized(f.getImage().getThumbnailImage(), 3);
	}

	private AtomicInteger idCounter = new AtomicInteger(0);
	
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Shot finishShot(Shot shot){
		
		if(shot == null){
			return null;
		}
		
		int shotNumber = idCounter.incrementAndGet();
		long shotId = (((long)movieId) << 16) | shotNumber;
		
		shot.setShotId(shotId);
		addSubtitleItems(shot);
		
		
		PersistentTuple tuple = this.pwriter.makeTuple(shotId, shotNumber, movieId, shot.getStart(), shot.getEnd());
		this.pwriter.write(tuple);
		

		
		return shot;
	}
	
	private void addSubtitleItems(Shot shot){
		int start = shot.getStart();
		int end = shot.getEnd();
		for(SubTitle st : this.subtitles){
			for(int i = 1; i <= st.getNumerOfItems(); ++i){
				SubtitleItem si = st.getItem(i);
				if(si == null || start > si.getEndFrame() || end < si.getStartFrame()){
					continue;
				}
				shot.addSubtitleItem(si);
			}
		}
	}
	
}

class DoublePair<K>{
	K first;
	double second;
	
	DoublePair(K first, double second){
		this.first = first;
		this.second = second;
	}
}