
import com.esotericsoftware.kryonet.*;

public class NetworkSmokeTest {
    public static void main(String[] args) throws Exception {
        if(args.length > 0 && args[0].equals("server")){
            Server server = new Server();

            server.addListener(new Listener(){
                @Override
                public void connected(Connection c){
                    System.out.println("SERVER: client connected, id=" + c.getID());
                    System.out.flush();
                }

                @Override
                public void disconnected(Connection c){
                    System.out.println("SERVER: client disconnected, id=" + c.getID());
                    System.out.flush();
                }
            });

            server.start();
            server.bind(5001);
            System.out.println("SERVER: listening on 5001");
            System.out.flush();

            for(int i = 0; i < 30; i++){
                Thread.sleep(1000);
                System.out.println("SERVER: still alive, tick " + i);
                System.out.flush();
            }
        } else {
            Client client = new Client();
            client.start();
            client.addListener(new Listener(){
                @Override
                public void connected(Connection c){
                    System.out.println("CLIENT: connected to server!");
                }
                @Override
                public void disconnected(Connection c){
                    System.out.println("CLIENT: disconnected");
                }
            });
            client.connect(5000, "127.0.0.1", 5001);
            System.out.println("CLIENT: connect() returned, waiting...");
            Thread.sleep(5000);
        }
    }
}