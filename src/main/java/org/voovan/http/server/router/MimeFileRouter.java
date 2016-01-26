package org.voovan.http.server.router;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.util.Date;

import org.voovan.http.server.HttpBizHandler;
import org.voovan.http.server.HttpRequest;
import org.voovan.http.server.HttpResponse;
import org.voovan.http.server.MimeTools;
import org.voovan.http.server.exception.ResourceNotFound;
import org.voovan.tools.TDateTime;
import org.voovan.tools.TFile;
import org.voovan.tools.THash;


/**
 * MIME 文件路由处理类
 * 
 * @author helyho
 *
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class MimeFileRouter implements HttpBizHandler {

	private String	rootPath;

	public MimeFileRouter(String rootPath) {
		this.rootPath = rootPath;
	}

	@Override
	public void Process(HttpRequest request, HttpResponse response) throws Exception {
		String urlPath = request.protocol().getPath();
		if (MimeTools.isMimeFile(urlPath)) {
			// 获取扩展名
			String fileExtension = urlPath.substring(urlPath.lastIndexOf(".") + 1, urlPath.length());
			// 根据扩展名,设置 MIME 类型
			response.header().put("Content-Type", MimeTools.getMimeByFileExtension(fileExtension));
			// 转换请求Path 里的文件路劲分割符为系统默认分割符
			urlPath = urlPath.replaceAll("//", File.separator);
			// 拼装文件实际存储路径
			String filePath = rootPath + urlPath;
			File responseFile = new File(filePath);
			
			if (responseFile.exists()) {
				if(isNotModify(responseFile,request,response)){
					return ;
				}else{
					fillMimeFile(responseFile, request, response);
				}
			}else{
				throw new ResourceNotFound(urlPath);
			}
		}
	}
	
	/**
	 * 判断是否是304 not modify
	 * @param responseFile
	 * @param request
	 * @param response
	 * @return
	 * @throws ParseException
	 */
	public boolean isNotModify(File responseFile,HttpRequest request,HttpResponse response) throws ParseException{
		//文件的 ETag
		String eTag = "\"" + THash.encryptMD5(Integer.toString(responseFile.hashCode())).toUpperCase() + "\"";
		
		//请求中的 ETag
		String requestETag = request.header().get("If-None-Match");
		
		//文件的修改日期
		Date fileModifyDate = new Date(responseFile.lastModified());
		
		//请求中的修改时间
		Date requestModifyDate = null;
		if(request.header().contain("If-Modified-Since")){
			requestModifyDate = TDateTime.parseToGMT(request.header().get("If-Modified-Since"));
		}
		
		//设置文件 hashCode
		response.header().put("ETag", eTag);
		//设置最后修改时间
		response.header().put("Last-Modified",TDateTime.formatToGMT(fileModifyDate));
		//设置缓存控制
		response.header().put("Cache-Control", "max-age=86400");
		//设置浏览器缓存超时控制
		response.header().put("Expires",TDateTime.formatToGMT(new Date(System.currentTimeMillis()+86400*1000)));
		
		//文件 hashcode 无变化,则返回304
		if(requestETag!=null && requestETag.equals(eTag)){
			setNotModifyResponse(response);
			return true;
		}
		//文件更新时间比请求时间大,则返回304
		if(requestModifyDate!=null && requestModifyDate.equals(fileModifyDate)){
			setNotModifyResponse(response);
			return true; 
		} 
		return false;
	}
	
	/**
	 * 填充 mime 文件到 response
	 * @param responseFile
	 * @param request
	 * @param response
	 * @throws FileNotFoundException 
	 */
	public void fillMimeFile(File responseFile,HttpRequest request,HttpResponse response){
		byte[] fileByte = null;
		int fileSize = TFile.getFileSize(responseFile.getPath());
		
		// 如果包含取一个范围内的文件内容进行处理,形似:Range: 0-800
		if (request.header().get("Range") != null && request.header().get("Range").contains("-")) {
			
			int beginPos=-1;
			int endPos=-1;
			
			String rangeStr = request.header().get("Range");
			rangeStr = rangeStr.replace("bytes=", "").trim();
			String[] ranges = rangeStr.split("-");
			
			//形似:Range: -800
			if(rangeStr.startsWith("-") && ranges.length==1){
				beginPos = fileSize - Integer.parseInt(ranges[0]);
				endPos = fileSize;
			}
			//形似:Range: 800-
			else if(rangeStr.endsWith("-") && ranges.length==1){
				beginPos = Integer.parseInt(ranges[0]);
				endPos = fileSize;
			}
			//形似:Range: 0-800
			else if(ranges.length==2){
				beginPos = Integer.parseInt(ranges[0]);
				endPos   = Integer.parseInt(ranges[1]);
			}
			fileByte = TFile.loadFileFromSysPath(responseFile.getPath(), beginPos, endPos);
			response.header().put("Content-Range", "bytes " + rangeStr + "/" + fileSize);
		} else {
			fileByte = TFile.loadFileFromSysPath(responseFile.getPath());
		}

		if (fileByte != null) {
			response.write(fileByte);
		}
		
	}
	
	/**
	 * 将响应报文设置称304
	 * @param response
	 */
	public void setNotModifyResponse(HttpResponse response){
		response.protocol().setStatus(304);
		response.protocol().setStatusCode("Not Modified");
	}

}