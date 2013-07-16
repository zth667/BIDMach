package BIDMach
import BIDMat.{Mat,BMat,CMat,CSMat,Dict,DMat,FMat,GMat,GIMat,GSMat,HMat,IDict,IMat,SMat,SDMat}
import BIDMat.MatFunctions._
import BIDMat.SciFunctions._
import HMat._
import scala.actors._
import java.io._

class Featurizer(val opts:Featurizer.Options = new Featurizer.Options) {
  	
  def mergeDicts(rebuild:Boolean,dictname:String="dict.gz",wcountname:String="wcount.gz"):Dict = {
  	val dd = new Array[Dict](6)
  	val md = new Array[Dict](6)
  	val yd = new Array[Dict](5)
  	var dy:Dict = null
  	var nmerged = 0
  	var ndone = 0
	  for (d <- opts.nstart to opts.nend) {
	    val (year, month, day) = Featurizer.decodeDate(d)
	    if (day == 1) ndone = 0
	  	val fm = new File(opts.fromMonthDir(d) + wcountname)
	    if (rebuild || ! fm.exists) {
	    	val fd = new File(opts.fromDayDir(d) + wcountname)
	    	if (fd.exists) {
	    		val bb = HMat.loadBMat(opts.fromDayDir(d) + dictname)
	    		val cc = HMat.loadIMat(opts.fromDayDir(d) + wcountname)
	    		dd(ndone % 6) = Dict(bb, cc, opts.threshold)
	    		ndone = ndone + 1
	    		print("-")
	    		if (ndone % 6 == 0) {
	    			md(ndone / 6 - 1) = Dict.union(dd:_*)
	    			print("+")
	    		}
	    	}
	    	if (day == 31 || d == opts.nend - 1) {	    	  
	  			if (ndone % 6 != 0) {
	  				md(ndone / 6) = Dict.union(dd.slice(0, ndone % 6):_*)
	  				print("+")
	  			}
	  			if (ndone > 0) {
	  				val dx = Dict.union(md.slice(0, (ndone-1)/6+1):_*)
	  				val (sv, iv) = sortdown2(dx.counts)
	  				val dxx = Dict(dx.cstr(iv), sv)
	  				HMat.saveBMat(opts.fromMonthDir(d)+dictname, BMat(dxx.cstr))
	  				HMat.saveDMat(opts.fromMonthDir(d)+wcountname, dxx.counts)
	  			}
	    	}
	    }
	    if (day == 31 || d == opts.nend - 1) {
	  		val fm = new File(opts.fromMonthDir(d) + wcountname)
	  		if (fm.exists) {
	  			val bb = HMat.loadBMat(opts.fromMonthDir(d) + dictname)
	  			val cc = HMat.loadDMat(opts.fromMonthDir(d) + wcountname)
	  			yd(nmerged % 5) = Dict(bb, cc, 4*opts.threshold)
	  			nmerged += 1
	  			print("*")
	  			if (nmerged % 5 == 0) {
	  			  val dm = Dict.union(yd:_*)
	  			  if (nmerged == 5) {
	  			    dy = dm
	  			  } else {
	  			  	dy = Dict.union(dy, dm)
	  			  }
	  			}
	  		}
	  	}
	  }
  	if (nmerged % 5 != 0) {
  		val dm = Dict.union(yd.slice(0, nmerged % 5):_*)
  		dy = Dict.union(dy, dm)
  	}
  	println
  	val (sv, iv) = sortdown2(dy.counts)
  	val dyy = Dict(dy.cstr(iv), sv)
  	HMat.saveBMat(opts.fromDir + dictname, BMat(dyy.cstr))
  	HMat.saveDMat(opts.fromDir + wcountname, dyy.counts)
  	dyy
	}
  
  def mergeIDicts(rebuild:Boolean,dictname:String="bdict.lz4",wcountname:String="bcnts.lz4"):IDict = {
    val alldict = Dict(HMat.loadBMat(opts.mainDict))
  	val dd = new Array[IDict](6)
  	val md = new Array[IDict](6)
  	var dy:IDict = null
  	var nmerged = 0
  	var ndone = 0
	  for (d <- opts.nstart to opts.nend) {
	    val (year, month, day) = Featurizer.decodeDate(d)
	    if (day == 1) ndone = 0
	  	val fm = new File(opts.fromMonthDir(d) + wcountname)
	    if (rebuild || ! fm.exists) {
	    	val fd = new File(opts.fromDayDir(d) + wcountname)
	    	if (fd.exists) {
	    	  val dict = Dict(HMat.loadBMat(opts.fromDayDir(d) + opts.localDict))
	    	  val map = dict --> alldict
	    		val bb = HMat.loadIMat(opts.fromDayDir(d) + dictname)
	    		val cc = HMat.loadDMat(opts.fromDayDir(d) + wcountname)
	    		val bm = map(bb)
	    		val igood = find(min(bm, 2) >= 0)
	    		val bg = bm(igood,?)
	    		val cg = cc(igood)
	    		val ip = icol(0->igood.length)
	    		IDict.sortlex2or3cols(bg, ip)
	    		IDict.treeAdd(IDict(bg, cg(ip), opts.threshold), dd)
	    		print("-")
	    	}
	    	if (day == 31 || d == opts.nend - 1) {	    	  
	    		val dx = IDict.treeFlush(dd)
	    		if (dx != null) {
	  				val (sv, iv) = sortdown2(dx.counts)
	  				HMat.saveIMat(opts.fromMonthDir(d)+dictname, dx.grams(iv,?))
	  				HMat.saveDMat(opts.fromMonthDir(d)+wcountname, sv)
	  			}
	    	}
	    }
	    if (day == 31 || d == opts.nend - 1) {
	  		val fm = new File(opts.fromMonthDir(d) + wcountname)
	  		if (fm.exists) {
	  			val bb = HMat.loadIMat(opts.fromMonthDir(d) + dictname)
	  			val cc = HMat.loadDMat(opts.fromMonthDir(d) + wcountname)
	  			val ip = icol(0->cc.length)
	  			val iss = IDict.sortlex2or3cols(bb, ip)
	    		IDict.treeAdd(IDict(bb, cc(ip), 4*opts.threshold), md)
	  		}
	  	}
	  }
  	dy = IDict.treeFlush(md)
  	println
  	val (sv, iv) = sortdown2(dy.counts)
  	val dyy = IDict(dy.grams(iv,?), sv)
  	HMat.saveIMat(opts.fromDir + dictname, dyy.grams)
  	HMat.saveDMat(opts.fromDir + wcountname, dyy.counts)
  	dyy
	}
  
  def gramDicts = {
    val nthreads = math.max(1, Mat.hasCUDA)
      
    for (ithread <- 0 until nthreads) {
      Actor.actor {
        setGPU(ithread)
      	val bigramsx = IMat(opts.guessSize, 2)
      	val trigramsx = IMat(opts.guessSize, 3)
      	val bdicts = new Array[IDict](5)
      	val tdicts = new Array[IDict](5)

      	for (idir <- (opts.nstart+ithread) until opts.nend by nthreads) {
      		val fname = opts.fromDayDir(idir)+opts.localDict
      		val fnew = opts.fromDayDir(idir)+"tcnts.lz4"
      		if (fileExists(fname) && !fileExists(fnew)) {
      			val dict = Dict(loadBMat(fname))
      			val isstart = dict(opts.startItem)
      			val isend = dict(opts.endItem)
      			val itstart = dict(opts.startText)
      			val itend = dict(opts.endText)
      			val ioverrun = dict(opts.overrun)
      			for (ifile <- 0 until 24) { 
      				val fn = opts.fromDayDir(idir)+opts.fromFile(ifile)
      				if (fileExists(fn)) {
      					val idata = loadIMat(fn)
      					var active = false
      					var intext = false
      					var istatus = -1
      					var nbi = 0
      					var ntri = 0
      					var len = idata.length
      					var i = 0
      					while (i < len) {
      						val tok = idata.data(i)-1
      						if (tok >= 0) {
//      							  println("all: "+dict(tok))
      							if (tok == isstart) {
      								active = true
      								istatus += 1
      							} else if (tok == itstart && active) {     							  
      								intext = true
      							} else if (tok == itend || tok == ioverrun) {
      								intext = false
      							} else if (tok == isend) {
      								intext = false
      								active = false
      							} else {
      							  if (intext) {
      							  	val tok1 = idata.data(i-1)-1
      							  	if (tok1 >= 0) {   
      							  		//      										println("txt: "+dict(tok))
      							  		if (tok1 != itstart) {
      							  			//      											  println("txt: "+alldict(tok1)+" "+alldict(tok))
      							  			bigramsx(nbi, 0) = tok1
      							  			bigramsx(nbi, 1) = tok
      							  			nbi += 1
      							  			val tok2 = idata.data(i-2)-1
      							  			if (tok2 >= 0) {
      							  				if (tok2 != itstart) {
      							  					trigramsx(ntri, 0) = tok2
      							  					trigramsx(ntri, 1) = tok1
      							  					trigramsx(ntri, 2) = tok
      							  					ntri += 1
      							  				}
      							  			}
      							  		}
      							  	}
      								}
      							}
      						}
      						i += 1
      					}
//      					println("bi=%d, tri=%d" format (nbi, ntri))
      					val bigrams = bigramsx(0->nbi, ?)
      					val bid = IDict.dictFromData(bigrams)
      					val trigrams = trigramsx(0->ntri, ?)
      					val trid = IDict.dictFromData(trigrams)
//      					println("bid=%d, trid=%d" format (bid.length, trid.length))
      					IDict.treeAdd(bid, bdicts)
      					IDict.treeAdd(trid, tdicts)      		
      				} 
      			}
//      			println("merging")
//      			tic
      			val bf = IDict.treeFlush(bdicts)
      			val tf = IDict.treeFlush(tdicts)
      			saveIMat(opts.fromDir(idir) + "bdict.lz4", bf.grams)
      			saveDMat(opts.fromDir(idir) + "bcnts.lz4", bf.counts)
      			saveIMat(opts.fromDir(idir) + "tdict.lz4", tf.grams)
      			saveDMat(opts.fromDir(idir) + "tcnts.lz4", tf.counts)
      			print(".")
      		}
      	}
      }
    }
  }
  
  def fileExists(fname:String) = {
    val testme = new File(fname)
    testme.exists
  }
}

object Featurizer {
  def encodeDate(yy:Int, mm:Int, dd:Int) = (372*yy + 31*mm + dd)
  
  def decodeDate(n:Int):(Int, Int, Int) = {
    val yy = (n - 32) / 372
    val days = n - 32 - 372 * yy
    val mm = days / 31 + 1
    val dd = days - 31 * (mm - 1) + 1
    (yy, mm, dd)
  }
  
  def dirxMap(fname:String):(Int)=>String = {
    (n:Int) => {    
    	val (yy, mm, dd) = decodeDate(n)
    	(fname format (n % 16, yy, mm, dd))
    }    
  }
  
  def dirMap(fname:String):(Int)=>String = {
    (n:Int) => {    
    	val (yy, mm, dd) = decodeDate(n)
    	(fname format (yy, mm, dd))
    }    
  }
  
  class Options {
    var fromDayDir:(Int)=>String = dirxMap("/disk%02d/twitter/tokenized/%04d/%02d/%02d/")
    var fromDir = "/big/twitter/tokenized/"
    var fromMonthDir:(Int)=>String = dirMap(fromDir + "%04d/%02d/")
    var fromYearDir:(Int)=>String = dirMap(fromDir + "%04d/")
    var toDayDir:(Int)=>String = dirMap("/disk%02d/twitter/featurized/%04d/%02d/%02d/") 
    var fromFile:(Int)=>String = (n:Int) => ("tweet%02d.gz" format n)
    var toFile:(Int)=>String = (n:Int) => ("tweet%02d.txt" format n)
    var localDict:String = "dict.gz"
    var nstart:Int = encodeDate(2011,11,22)
    var nend:Int = encodeDate(2013,3,3)
    var startItem:String = "<status>"
    var endItem:String = "</status>"
    var startText:String = "<text>"
    var endText:String = "</text>"
    var overrun:String = "<user>"
    var mainDict:String = "/big/twitter/tokenized/alldict.gz"
    var mainCounts:String = "/big/twitter/tokenized/allwcount.gz"
    var threshold = 10
    var guessSize = 100000000
    var nthreads = 4
  }
  
 
}