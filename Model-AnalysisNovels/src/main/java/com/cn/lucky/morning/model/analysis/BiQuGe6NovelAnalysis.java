package com.cn.lucky.morning.model.analysis;

import com.cn.lucky.morning.model.common.cache.CacheService;
import com.cn.lucky.morning.model.common.constant.Const;
import com.cn.lucky.morning.model.common.log.Logs;
import com.cn.lucky.morning.model.common.mvc.MvcResult;
import com.cn.lucky.morning.model.common.network.Col;
import com.cn.lucky.morning.model.common.network.NetWorkUtil;
import com.cn.lucky.morning.model.domain.BookInfo;
import com.google.common.collect.Maps;
import okhttp3.Headers;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BiQuGe6NovelAnalysis {
    private static final Logger logger = Logs.get();
    public static final String BASE_URL = "https://www.xbiquge6.com";
    private static final String SOURCE_NAME = "新笔趣阁";
    private static final String IMG_ONERROR = "$(this).attr('src', '/imgs/nocover.jpg')";
    private Headers headers;

    @Autowired
    private CacheService cacheService;

    public BiQuGe6NovelAnalysis() {
        headers = new Headers.Builder()
                .add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.97 Safari/537.36")
                .build();
    }

    /**
     * 通过名称查找书籍
     *
     * @param name
     * @return
     */
    public MvcResult searchByName(String name) {
        String url = String.format(BASE_URL + "/search.php?keyword=%s", name);
        MvcResult result = MvcResult.create();
        try {
            List<BookInfo> list = (List<BookInfo>) cacheService.get(Const.cache.BOOK_SEARCH_RESULT + url);
            if (list == null) {
                list = new ArrayList<>();
                Response response = NetWorkUtil.get(url, headers, true);
                Document html = Jsoup.parse(response.body().string());
                Elements divs = html.select(".result-game-item");
                for (Element div : divs) {
                    BookInfo info = new BookInfo();
                    info.setName(div.selectFirst(".result-game-item-title-link").text());
                    info.setBookUrl(div.selectFirst(".result-game-item-title-link").attr("href"));
                    info.setBookImg(div.selectFirst(".result-game-item-pic-link-img").attr("src"));
                    info.setBookImgError(IMG_ONERROR);
                    info.setNovelDes(div.selectFirst(".result-game-item-desc").text());
                    Elements infos = div.select(".result-game-item-info-tag");
                    info.setAuthor(infos.get(0).child(1).text());
                    info.setNovelType(infos.get(1).child(1).text());
                    info.setLastUpdate(infos.get(2).child(1).text());
                    info.setLastNew(infos.get(3).child(1).text());
                    info.setBookSourceLink(info.getBookUrl());
                    info.setBookSourceName(SOURCE_NAME);
                    list.add(info);
                }
                cacheService.set(Const.cache.BOOK_SEARCH_RESULT + url, list, Const.cache.BOOK_SEARCH_RESULT_TTL);
            }
            result.addVal("list", list);
        }catch (SocketTimeoutException e){
            logger.error("查找书籍出错",e);
            result.setSuccess(false);
            result.setMessage("《"+SOURCE_NAME+"》网络连接超时");
        }catch (Exception e) {
            logger.error("查找书籍出错",e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    /**
     * 获取书籍详细信息页
     *
     * @param url
     * @return
     */
    public MvcResult loadBookInfo(String url) {
        MvcResult result = MvcResult.create();
        try {
            Map<String, Object> map = (Map<String, Object>) cacheService.get(Const.cache.BOOK_DETAIL + url);
            if (map == null) {
                map = Maps.newHashMap();
                Response response = NetWorkUtil.get(url, headers, true);
                Document html = Jsoup.parse(response.body().string());
                Element infoDiv = html.selectFirst("#info");
                BookInfo bookInfo = new BookInfo();
                bookInfo.setName(infoDiv.child(0).text());
                String author = infoDiv.child(1).text();
                bookInfo.setAuthor(author.substring(author.indexOf("：") + 1));
                String lastUpdate = infoDiv.child(3).text();
                bookInfo.setLastUpdate(lastUpdate.substring(lastUpdate.indexOf("：") + 1));
                bookInfo.setLastNew(infoDiv.child(4).selectFirst("a").text());

                Element introDiv = html.selectFirst("#intro");
                bookInfo.setNovelDes(introDiv.child(0).text());

                Element fmimgDiv = html.selectFirst("#fmimg");
                bookInfo.setBookImg(fmimgDiv.child(0).attr("src"));
                bookInfo.setBookImgError(IMG_ONERROR);
                bookInfo.setBookUrl(url);
                bookInfo.setBookSourceLink(bookInfo.getBookUrl());
                bookInfo.setBookSourceName(SOURCE_NAME);
                map.put("info", bookInfo);

                List<Col> catalogs = new ArrayList<>();

                Element listDiv = html.selectFirst("#list");
                Elements dds = listDiv.select("dd");
                for (Element dd : dds) {
                    Element link = dd.child(0);
                    String name = link.text();
                    String href = link.attr("href");
                    href = BASE_URL + href;
                    catalogs.add(new Col(name, href));
                }

                map.put("catalogs", catalogs);

                cacheService.set(Const.cache.BOOK_DETAIL + url, map, Const.cache.BOOK_DETAIL_TTL);
            }
            result.setSuccess(true);
            result.addAllVal(map);


        }catch (SocketTimeoutException e){
            logger.error("获取书籍详情出错",e);
            result.setSuccess(false);
            result.setMessage("《"+SOURCE_NAME+"》网络连接超时");
        } catch (Exception e) {
            logger.error("获取书籍详情出错",e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    /**
     * 获取章节内容
     *
     * @param url
     * @return
     */
    public MvcResult loadContent(String url) {
        MvcResult result = MvcResult.create();
        try {

            Map<String, Object> map = (Map<String, Object>) cacheService.get(Const.cache.BOOK_CATALOG_CONTENT + url);
            if (map == null) {
                map = Maps.newHashMap();
                Response response = NetWorkUtil.get(url, headers, true);
                Document html = Jsoup.parse(response.body().string());
                Element name = html.selectFirst(".bookname");
                map.put("catalogName", name.child(0).text());

                Element div = html.selectFirst("#content");
                String content = div.html();
                while (content.indexOf("\n<br>\n<br>") != -1) {
                    content = content.replaceAll("\n<br>\n<br>", "<br>");
                }
                content.replaceAll("\n<br>", "<br>");
                map.put("content", content);

                Element bottom = html.selectFirst(".bottem2");
                Elements links = bottom.select("a");
                String preCatalog = links.get(0).attr("href");
                if (preCatalog.endsWith(".html")) {
                    map.put("preCatalog", BASE_URL + preCatalog);
                }
                String catalogs = links.get(1).attr("href");
                map.put("catalogs", BASE_URL + catalogs);
                String nextCatalog = links.get(2).attr("href");
                if (nextCatalog.endsWith(".html")) {
                    map.put("nextCatalog", BASE_URL + nextCatalog);
                }

                cacheService.set(Const.cache.BOOK_CATALOG_CONTENT + url, map, Const.cache.BOOK_CATALOG_CONTENT_TTL);
            }
            result.setSuccess(true);
            result.addAllVal(map);

        }catch (SocketTimeoutException e){
            logger.error("获取书籍章节内容出错",e);
            result.setSuccess(false);
            result.setMessage("《"+SOURCE_NAME+"》网络连接超时");
        } catch (Exception e) {
            logger.error("获取书籍章节内容出错",e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }
}
