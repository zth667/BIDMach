package BIDMach.allreduce

import BIDMat.{Mat,SBMat,CMat,DMat,FMat,IMat,HMat,GDMat,GLMat,GMat,GIMat,GSDMat,GSMat,LMat,SMat,SDMat}
import BIDMat.MatFunctions._
import BIDMat.SciFunctions._
import edu.berkeley.bid.comm._
import scala.collection.parallel._
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

class Response(
  val rtype:Int, round0:Int, val src:Int, val clen:Int, val bytes:Array[Byte], val blen:Int
) {
  val magic = Response.magic;
  var round = round0;
  val byteData = ByteBuffer.wrap(bytes);
  val intData = byteData.asIntBuffer;
  val floatData = byteData.asFloatBuffer;
  val longData = byteData.asLongBuffer;
  
  def encode() = {}
  def decode() = {}
  
  def this(rtype0:Int, round0:Int, dest0:Int, clen0:Int) =
    this(rtype0, round0, dest0, clen0, new Array[Byte](4*clen0), 4*clen0);
  
  def this(rtype0:Int, round0:Int, dest0:Int) = this(rtype0, round0, dest0, 0, null, 0);
  
  override def toString():String = {
    "Response %s, round %d, src %d, length %d bytes" format (Command.names(rtype), round, src, clen*4);
  }
  
}

class WorkerProgressResponse(round0:Int, src0:Int, progress0:WorkerProgress, bytes:Array[Byte])
  extends Response(Command.workerProgressCtype, round0, src0, bytes.size, bytes, bytes.size) {

  var progress:WorkerProgress = progress0;

  // round0 and dest0 are discarded
  def this(progress0:WorkerProgress) = {
    this(0, 0, progress0, {
      val out  = new ByteArrayOutputStream()
      val output = new ObjectOutputStream(out)
      output.writeObject(progress0)
      output.close
      out.toByteArray()
    });
  }

  def this(progress0:WorkerProgress, bytes:Array[Byte]) = {
    this(0, 0, progress0, bytes);
  }

  override def encode () { }

  override def decode() {
    val in = new ByteArrayInputStream(bytes);
    val input = new ObjectInputStream(in);
    bandwidth = input.readObject.asInstanceOf[Bandwidth];
    input.close;
  }
}


class AllreduceResponse(round0:Int, src0:Int, bytes:Array[Byte])
extends Response(Command.allreduceCtype, round0, src0, 1, bytes, 1*4) {

  def this(round0:Int, src0:Int) = this(round0, src0, new Array[Byte](1*4));

  override def encode():Unit = {
    intData.rewind();
    intData.put(src);
  }

  override def decode():Unit = {}
}

class LearnerDoneResponse(round0:Int, src0:Int, bytes:Array[Byte])
extends Response(Command.learnerDoneCtype, round0, src0, 1, bytes, 1*4) {

  def this(round0:Int, src0:Int) = this(round0, src0, new Array[Byte](1*4));

  override def encode():Unit = {
    intData.rewind();
    intData.put(src);
  }

  override def decode():Unit = {}
}


class ReturnObjectResponse(round0:Int, src0:Int, obj0:AnyRef, bytes:Array[Byte])
extends Response(Command.returnObjectCtype, round0, src0, bytes.size, bytes, bytes.size) {

  var obj:AnyRef = obj0;

  def this(round0:Int, dest0:Int, obj0:AnyRef) = {
    this(round0, dest0, obj0, {
      val out  = new ByteArrayOutputStream()
      val output = new ObjectOutputStream(out)
      output.writeObject(obj0)
      output.close
      out.toByteArray()
    });
  }
  
  override def encode ():Unit = { }
  
  override def decode():Unit = {    
  }
}

class ResponseWriter(address:InetSocketAddress, resp:Response, me:Worker) extends Runnable {

	def run() {
		var socket:Socket = null;
	try {
		socket = new Socket();
		socket.setReuseAddress(true);
		socket.connect(address, me.opts.sendTimeout);
		if (socket.isConnected()) {
			val ostr = new DataOutputStream(socket.getOutputStream());
			ostr.writeInt(resp.magic)
			ostr.writeInt(resp.rtype);
      ostr.writeInt(resp.round);
			ostr.writeInt(resp.src);
			ostr.writeInt(resp.clen);
	ostr.writeInt(resp.blen);
	ostr.write(resp.bytes, 0, resp.blen);
		}
	} catch {
	case e:Exception =>
	if (me.opts.trace > 0) {
		me.log("Master problem sending resp %s\n%s\n" format (resp.toString, Response.printStackTrace(e)));
	}
	} finally {
		try { if (socket != null) socket.close(); } catch {
		case e:Exception =>
		if (me.opts.trace > 0) me.log("Master problem closing socket\n%s\n" format Response.printStackTrace(e));        
		}
	}
	}
}

class ResponseReader(socket:Socket, me:Master) extends Runnable {
  def run() {
    try {
      val istr = new DataInputStream(socket.getInputStream());
      val magic = istr.readInt();
      val rtype = istr.readInt();
      val round = istr.readInt();
      val dest = istr.readInt();
      val clen = istr.readInt();
      val blen = istr.readInt();
      val response = new Response(rtype, round, dest, clen, new Array[Byte](blen), blen);
      if (me.opts.trace > 2) me.log("Master got packet %s\n" format (response.toString));
      istr.readFully(response.bytes, 0, blen);

      rtype match {
        case Command.allreduceCtype => {
          me.listener.allreduceCollected += 1
        }
        case Command.learnerDoneCtype => {
          me.stopUpdates()
          me.log("Stopping allreduce update!\n")
        }
        case _ =>
      }
      try {
        socket.close();
      } catch {
        case e:IOException => {if (me.opts.trace > 0) me.log("Master Problem closing socket "+Response.printStackTrace(e)+"\n")}
      }
      me.handleResponse(response);
    } catch {
      case e:Exception => if (me.opts.trace > 0) me.log("Master Problem reading socket "+Response.printStackTrace(e)+"\n");
    } finally {
      try {
        if (!socket.isClosed) socket.close();
      } catch {
        case e:IOException => {if (me.opts.trace > 0) me.log("Master Final Problem closing socket "+Response.printStackTrace(e)+"\n")}
      }
    }
  }
}

object Response {
	val magic = 0xa6b38734;
  
  def printStackTrace(e:Exception):String = {
    val baos = new ByteArrayOutputStream();
    val ps = new PrintStream(baos);
    e.printStackTrace(ps);
    val str = baos.toString();
    ps.close();
    str;
  }
}
