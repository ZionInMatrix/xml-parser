package consumer;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class XMLConsume {

    private static final String URL = "https://vdp.cuzk.cz/vymenny_format/soucasna/20211031_OB_573060_UZSZ.xml.zip";
    private static final String PATH = System.getProperty("user.dir") + "/src/main/resources/addressBook.zip";
    private static final String PATH_UNZIP = System.getProperty("user.dir") + "/src/main/resources";

    public static void main(String[] args) throws Exception {
        downloadAndUnzip();
        parseData();
    }

    /**
     * The method will allow download ZIP file from the URL address and Unzip it
     */
    public static void downloadAndUnzip() {
        FileOutputStream out = null;
        BufferedInputStream in = null;
        int count = 0;

        try {
            in = new BufferedInputStream(new URL(URL).openStream());
            out = new FileOutputStream(PATH);
            byte[] data = new byte[1024];

            while ((count = in.read(data, 0, 1024)) != -1) {
                out.write(data, 0, count);
                out.flush();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        unzipFile();
    }

    /**
     * The method will unzip file to special path
     */
    public static void unzipFile() {
        try {
            ZipFile zipFile = new ZipFile(PATH);
            zipFile.extractAll(PATH_UNZIP);

        } catch (ZipException e) {
            e.printStackTrace();
        }
    }

    /**
     * The method will connect to the database
     *
     * @throws SQLException if something goes wrong
     */
    public static Connection connectToDatabase() throws SQLException {
        String jdbcURL = "jdbc:mysql://localhost:3306/AddressBook";
        String userName = "root";
        String password = "yourpasswd";

        Connection connection = null;

        connection = DriverManager.getConnection(jdbcURL, userName, password);
        connection.setAutoCommit(false);

        return connection;
    }

    /**
     * The method will allow parse data to database
     *
     * @throws Exception if something goes wrong
     */
    public static void parseData() throws Exception {
        File file = new File(System.getProperty("user.dir") + "/src/main/resources/20211031_OB_573060_UZSZ.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document xmlDoc = builder.parse(file);

        XPath xpath = XPathFactory.newInstance().newXPath();

        getTextContent((Node) xpath.evaluate("/vf:Data/vf:Obce", xmlDoc, XPathConstants.NODESET), "vf:Obce");
    }

    /**
     * The method to extract an attribute value from a Node.
     *
     * @param node     the represented Node
     * @param attrName the name of child element
     * @return value of Node
     */
    public static String getAttrValue(Node node, String attrName) {
        if (!node.hasAttributes()) {
            return "";
        }

        NamedNodeMap nmap = node.getAttributes();

        Node n = nmap.getNamedItem(attrName);
        if (n == null) {
            return "";
        }
        return n.getNodeValue();
    }

    /**
     * The method will allow us to extract the text content of a named child element
     *
     * @param parentNode the represented Node
     * @param childName  The name of child element
     * @return text
     * @throws SQLException if something goes wrong
     */
    public static String getTextContent(Node parentNode, String childName) throws SQLException {
        NodeList nList = parentNode.getChildNodes();
        Connection connection = connectToDatabase();

        String sql = "INSERT INTO obec(kod, nazev) VALUES(?,?)";

        PreparedStatement stmt = connection.prepareStatement(sql);

        for (int i = 0; i < nList.getLength(); i++) {
            Node n = nList.item(i);
            String name = n.getNodeName();
            if (name.equals(childName)) {
                return n.getTextContent();
            }
            for (int j = 0; j < nList.getLength(); j++) {
                Node node = nList.item(j);
                List<String> columns = Arrays.asList(getAttrValue(node, "vf:Obce"),
                        getTextContent(node, "obi:kod"), getTextContent(node, "obi:nazev"));
                for (int k = 0; k < columns.size(); k++) {
                    stmt.setString(k + 1, columns.get(j));
                }
            }
            stmt.execute();
            connection.close();
        }
        return "";
    }
}
