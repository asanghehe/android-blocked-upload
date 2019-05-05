
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/attachment")
public class AttachmentController {
	
	/**
	 * 断点续传接收方法
	 *  每次请求传递一个文件的部分byte数据
	 * @return
	 * @throws IOException 
	 */
	@ResponseBody
	@RequestMapping("breakpoint-renewal")
	public Object breakpointRenewal(@RequestParam(value="file") MultipartFile file,
			@RequestParam(value="filemd5", required=true) String fileMd5,
			@RequestParam(value="visId", required=true) String visId,
			@RequestParam(value="blockIndex", required=true) Long blockIndex,
			@RequestParam(value="blockSize", required=true) Long blockSize,
			@RequestParam(value="filesize", required=true) Long filesize) throws IOException {
		
		String module = "app-upload";
		String path =  "D://upload-dir/" + module+"/";
		
		String extension = FilenameUtils.getExtension(file.getOriginalFilename());;
		String fileWithPath = path +fileMd5 + "." + extension;		//最终文件
		
		//上传过程中的临时文件，在文件夹中可以以临时文件区分出是在上传过程中的还是已经上传完的文件
		String fileOfTmp = path +fileMd5 + ".upload.tmp";
		
		byte[] bts = file.getBytes();
		
		File tmp = new File(fileOfTmp);
		if(!tmp.exists()) {
			tmp.createNewFile();
		}
		
		RandomAccessFile raf = new RandomAccessFile(tmp, "rwd");
		//不管原来文件有多大，将文件大小设置为指定大小
		raf.setLength(filesize);
		
		//文件指针跳转到指定位置，并写下接收到的数据块
		raf.seek((blockIndex-1) * blockSize);
		raf.write(bts, 0, bts.length);
		raf.close();
		
		//org.apache.commons.io.FileUtils.writeByteArrayToFile(target, bts, true);
		
		//最后一个区块处理的时候，执行业务操作，比如插入数据库，将上传的临时文件名改成正式文件名
		Long blockNums = (filesize % blockSize == 0) ? (filesize / blockSize) : (filesize/blockSize + 1);
		if(blockIndex >= blockNums) {

			File target = new File(fileWithPath);
			if(target.exists()) {
				target.delete();
			}
			//将临时文件改名为正式文件
			FileUtils.moveFile(tmp, target);
			
			/** 正式的业务处理
			VisitAttachment a = new VisitAttachment();
			a.setExt(extension);
			a.setFileName(file.getOriginalFilename());
			//a.setUserId();		//从session中取用户id等
			a.setFilePath(target.getAbsolutePath());
			a.setUploadTime(System.currentTimeMillis() / 1000);
			if(StringUtils.isNumeric(visId)) {
				a.setVisId(Long.valueOf(visId));
			}else {
				a.setVisId(0L);
			}
			a.setFileSize(target.length());
			a.setId(fileMd5);
			
			//attachDao.insert(a);
			 **/
		}
		
		return AsyncJsonResult.build(true, "block 上传成功");
	}
	
	public static class AsyncJsonResult {
		
		private boolean success;
		
		private String msg;
		
		private Object data;
		
		public static AsyncJsonResult build(boolean success, String msg){

			AsyncJsonResult re = new AsyncJsonResult();
			re.setMsg(msg);
			re.setSuccess(success);
			re.setData(null);
			
			return re;
		}
		
		public static AsyncJsonResult build(boolean success, String msg, Object data){

			AsyncJsonResult re = new AsyncJsonResult();
			re.setMsg(msg);
			re.setSuccess(success);
			re.setData(data);
			
			return re;
		}
		
		public boolean isSuccess() {
			return success;
		}

		public void setSuccess(boolean success) {
			this.success = success;
		}

		public String getMsg() {
			return msg;
		}

		public void setMsg(String msg) {
			this.msg = msg;
		}

		public Object getData() {
			return data;
		}

		public void setData(Object data) {
			this.data = data;
		}
	}
}
