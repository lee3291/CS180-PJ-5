import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements Runnable {
    private User currentUser; // the client user
    private final Socket socket; // the client socket
    public final static Object sentinel = new Object(); // gatekeeper object for concurrency
    private static ConcurrentHashMap<String, User> users; // all the users

    /**
     * Initiate a new server object
     *
     * @param socket the client object associate with this server
     */
    public Server(Socket socket) {
        this.socket = socket;
    }

    /**
     * Send an ArrayList of String containing the {@link #currentUser}'s purchase history
     *
     * @param output the output stream to communicate with client
     */
    private void getPurchaseHistory(ObjectOutputStream output) throws IOException {
        // @Ethan
        // Assume this user must be customer
        respondToClient(output, ((Customer) this.currentUser).getPurchaseHistory());
    }

    /**
     * Decrease the specified product quantity by the specified purchase quantity
     * Increase the Store's revenue
     * Add the current {@link #currentUser} to the {@link Store}'s customer field
     * Add the new sale history to the {@link Store}'s saleHistory field
     * Add the new purchase history to the {@link #currentUser} purchaseHistory field
     * Send a TRUE boolean if success
     * Send a FALSE boolean if failed (i.e. product is sold out)
     *
     * @param output         the output stream to communicate with client
     * @param storeName      the product's storeName
     * @param productName    the product's name
     * @param strPurchaseQty the quantity purchase
     */
    private void buyItem(ObjectOutputStream output,
                         String storeName, String productName, String strPurchaseQty) throws IOException {
        // Find store from all the stores.
        Store productStore = collectMarketStoreHM().get(storeName);

        synchronized (sentinel) { // TODO: checking concurrency
            for (Product p : productStore.getCurrentProducts()) {
                // Found the product
                if (p.getName().equalsIgnoreCase(productName)) {
                    int quantity = Integer.parseInt(strPurchaseQty);

                    // Making sure product is not sold out
                    if (quantity > p.getQuantity()) {
                        respondToClient(output, false);
                        return;
                    }

                    // Change data in appropriate location
                    p.setQuantity(p.getQuantity() - quantity);
                    productStore.setTotalRevenue(productStore.getTotalRevenue() + p.getPrice() * quantity);
                    productStore.addCustomerEmail(currentUser.getEmail());
                    productStore.addToSaleHistory(productStore.makeSaleDetail(p, quantity, currentUser.getEmail()));
                    ((Customer) currentUser).addToPurchaseHistory(p, quantity);

                    output.writeObject(true);
                    return;
                }
            }
            output.writeObject(false);
        }
    }

    /**
     * Loop through all Seller in the Market and collect all the Store
     *
     * @return an ArrayList of Store currently in the Marketplace
     */
    private ArrayList<Store> collectMarketStoreAL() {
        // @Ethan
        ArrayList<Store> allStores = new ArrayList<>();
        for (User user : users.values()) {
            if (user instanceof Seller) {
                for (Store store : ((Seller) user).getStores().values()) {
                    allStores.add(store);
                }
            }
        }
        return allStores;
    }

    /**
     * Loop through all Seller in the Market and collect all the Store
     *
     * @return an HashMap of Store currently in the Marketplace
     */
    private HashMap<String, Store> collectMarketStoreHM() {
        // @Ethan
        HashMap<String, Store> allStores = new HashMap<>();
        for (User user : users.values()) {
            if (user instanceof Seller) {
                allStores.putAll(((Seller) user).getStores());
            }
        }
        return allStores;
    }

    /**
     * Loop through all Store in the Marketplace and collect all the Product
     *
     * @return an ArrayList of Product currently in the Marketplace
     */
    private ArrayList<Product> collectMarketProduct() {
        // @Ethan
        ArrayList<Product> allProducts = new ArrayList<>();
        for (Store store : collectMarketStoreAL()) {
            allProducts.addAll(store.getCurrentProducts());
        }
        return allProducts;
    }

    /**
     * Get an ArrayList contains the Product from all existing Store within the Marketplace
     * If searchKey is '-1', send an ArrayList of all Product in the Marketplace
     * <p>
     * If searchKey IS NOT '-1',
     * send an ArrayList of Products that contain the searchKey in their name or description to client
     * ArrayList is empty if there is no matching Product
     *
     * @param output    the output stream to communicate with client
     * @param searchKey the key to search (i.e. the product name or description); '-1' if no search is needed
     */
    private void getMarketProduct(ObjectOutputStream output, String searchKey) throws IOException {
        // @Ethan
        ArrayList<Product> allProducts = collectMarketProduct();
        // No search
        if (searchKey.equals("-1")) {
            respondToClient(output, allProducts);

        } else { // Searching
            searchKey = searchKey.toLowerCase();
            ArrayList<Product> matchingProducts = new ArrayList<>();
            for (Product p : allProducts) {
                if (p.getName().toLowerCase().contains(searchKey) || // <- searchKey is in name
                        (p.getDescription().toLowerCase().contains(searchKey))) { // <- or searchKey is in description
                    // add matching product to matchingProducts
                    matchingProducts.add(p);
                }
            }
            respondToClient(output, matchingProducts);
        }
    }

    /**
     * Get an ArrayList contains the Product from all existing Store within the Marketplace
     * Sort the ArrayList in the ascending order by price
     *
     * @param output the output stream to communicate with client
     */
    private void sortProductPrice(ObjectOutputStream output) throws IOException {
        // @Ethan
        // this method was in Store class, but it's relocated to Server class.
        // Clone to protect original ArrayList
        ArrayList<Product> sortedProducts = (ArrayList<Product>) collectMarketProduct().clone();
        // Sorting in ascending order
        for (int i = 1; i < sortedProducts.size(); i++) {
            Product p = sortedProducts.get(i);
            int j = i - 1;
            while (j >= 0 && sortedProducts.get(j).getPrice() > p.getPrice()) {
                sortedProducts.set(j + 1, sortedProducts.get(j));
                j = j - 1;
            }
            sortedProducts.set(j + 1, p);
        }
        respondToClient(output, sortedProducts);
    }

    /**
     * Get an ArrayList contains the Product from all existing Store within the Marketplace
     * Sort the ArrayList in the ascending order by quantity
     *
     * @param output the output stream to communicate with client
     */
    private void sortProductQty(ObjectOutputStream output) throws IOException {
        // @Ethan
        // this method was in Store class, but it's relocated to Server class.
        // Clone to protect original ArrayList
        ArrayList<Product> sortedProducts = (ArrayList<Product>) collectMarketProduct().clone();
        // Sorting in ascending order
        for (int i = 1; i < sortedProducts.size(); i++) {
            Product p = sortedProducts.get(i);
            int j = i - 1;
            while (j >= 0 && sortedProducts.get(j).getQuantity() > p.getQuantity()) {
                sortedProducts.set(j + 1, sortedProducts.get(j));
                j = j - 1;
            }
            sortedProducts.set(j + 1, p);
        }
        respondToClient(output, sortedProducts);
    }

    /**
     * Remove the product from the specified store's product list
     * Send a TRUE boolean object to client if success
     * Send a FALSE boolean object to client if failed (i.e. the product does not exist in the specified store)
     *
     * @param output      output the output stream to communicate with client
     * @param storeName   the name of the Store that contain the product
     * @param productName the name of the product
     */
    private void deleteProduct(ObjectOutputStream output, String storeName, String productName) throws IOException {
        Store store = ((Seller) currentUser).getStores().get(storeName);
        if (store.removeProduct(productName)) {
            respondToClient(output, true);
        } else {
            respondToClient(output, false);
        }
    }

    /**
     * Modify an existing product in the specified store, which exist in the {@link #currentUser}'s store field
     * Send a TRUE boolean object to client if success
     * Send a FALSE boolean object to client if failed (i.e. product name is taken)
     *
     * @param output      the output stream to communicate with client
     * @param oldName     the old product name (used for searching)
     * @param newName     the new product name
     * @param storeName   the store name (not allowed to be changed; used to search)
     * @param description the description of the product (same if description is not changed)
     * @param strPrice    the price of the product (same if description is not changed)
     * @param strQuantity the quantity of the product (same if description is not changed)
     */
    private void modifyProduct(ObjectOutputStream output, String oldName, String newName, String storeName, String description,
                               String strPrice, String strQuantity) throws IOException {
        // Get the store
        Store store = ((Seller) currentUser).getStores().get(storeName); // TODO: concurrency?

        // Make sure new name is unique;
        if (!oldName.equalsIgnoreCase(newName)) {
            for (Product p : store.getCurrentProducts()) {
                if (p.getName().equalsIgnoreCase(newName)) {
                    respondToClient(output, false);
                    return;
                }
            }
        }

        // Find the product
        for (Product p : store.getCurrentProducts()) {
            if (p.getName().equalsIgnoreCase(oldName)) {
                // Modify product
                p.setName(newName);
                p.setDescription(description);
                p.setPrice(Double.parseDouble(strPrice));
                p.setQuantity(Integer.parseInt(strQuantity));

                respondToClient(output, true);
                return;
            }
        }
    }

    /**
     * Create a new product in the specified store, which exist in the {@link #currentUser}'s store field
     * Send a TRUE boolean object to client if success
     * Send a FALSE boolean object to client if failed (i.e. product's name is taken within the same store)
     *
     * @param output      the output stream to communicate with client
     * @param name        the new product name
     * @param storeName   the product's store
     * @param description the description of the product
     * @param strPrice    the price of the product
     * @param strQuantity the quantity of the product
     */
    private void addProduct(ObjectOutputStream output, String name, String storeName, String description,
                            String strPrice, String strQuantity) throws IOException {
        // Get the store
        Store store = ((Seller) currentUser).getStores().get(storeName); // TODO: concurrency?

        // Add the product
        Product newProduct = new Product(name, storeName, currentUser.getEmail(),
                description, Double.parseDouble(strPrice), Integer.parseInt(strQuantity));

        // Add to store
        if (store.addProduct(newProduct)) {
            respondToClient(output, true);
        } else {
            respondToClient(output, false);
        }
    }

    /**
     * Create an ArrayList contains the Products from the specified store
     * If searchKey is '-1', send an ArrayList of all Product in the Store to client
     * <p>
     * If searchKey IS NOT '-1',
     * send an ArrayList of Products that contain the searchKey in their name or description to client
     * ArrayList is empty if there is no matching Product
     *
     * @param output    the output stream to communicate with client
     * @param searchKey the key to search (i.e. the product name or description); '-1' if no search is needed
     */
    private void getStoreProduct(ObjectOutputStream output, String storeName, String searchKey) throws IOException {
        // @Ethan
        // Find store with sellerEmail and storeName
        Store specifiedStore = ((Seller) currentUser).getStores().get(storeName); // Desired Store of Seller
        ArrayList<Product> specifiedProducts = specifiedStore.getCurrentProducts(); // Desired Products of Store

        if (searchKey.equals("-1")) {
            ArrayList<Product> newProducts = new ArrayList<>(specifiedProducts);
            respondToClient(output, newProducts);
        } else {
            ArrayList<Product> matchingProducts = new ArrayList<>();
            searchKey = searchKey.toLowerCase();
            for (Product p : specifiedProducts) {
                String productName = p.getName().toLowerCase();
                String productDesc = p.getDescription().toLowerCase();
                if (productName.contains(searchKey) || productDesc.contains(searchKey)) {
                    matchingProducts.add(p);
                }
            }
            respondToClient(output, matchingProducts);
        }
    }

    /**
     * Create an ArrayList contains all the Store from the {@link #currentUser}'s store field
     * If searchKey is '-1', send an ArrayList of all Store to client
     * <p>
     * If searchKey IS NOT '-1',
     * send an ArrayList of Store that contain the searchKey in their name to client
     * ArrayList is empty if there is no matching Store
     *
     * @param output    the output stream to communicate with client
     * @param searchKey the key to search (i.e. the store name); '-1' if no search is needed
     */
    private void getSellerStores(ObjectOutputStream output, String searchKey) throws IOException {
        // @Ethan
        // Assume currentUser is instanceof Seller
        Seller currentSeller = (Seller) currentUser;
        HashMap<String, Store> sellerStoresHM = currentSeller.getStores();
        ArrayList<Store> sellerStoresAL = new ArrayList<>();
        if (searchKey.equals("-1")) {
            sellerStoresAL.addAll(sellerStoresHM.values());
        } else {
            // Find stores that the key String contains the searchKey String.
            for (String storeName : sellerStoresHM.keySet()) {
                if (storeName.toLowerCase().contains(searchKey.toLowerCase())) {
                    sellerStoresAL.add(sellerStoresHM.get(storeName));
                }
            }
        }
        respondToClient(output, sellerStoresAL);
    }

    /**
     * Find a store from all the stores of a seller, send to server.
     * Send null if store does not exist.
     *
     * @param output    the output stream to communicate with client
     * @param storeName the name of the store
     */
    private void findSellerStore(ObjectOutputStream output, String storeName) throws IOException {
        // @Ethan
        Seller currentSeller = (Seller) currentUser;
        respondToClient(output, currentSeller.getStores().get(storeName));
    }

    /**
     * Delete a store in the {@link #currentUser}'s stores field
     * Send a TRUE boolean object to client if success
     * Send a FALSE boolean object to client if failed (i.e. there is no store with matching name)
     *
     * @param output    the output stream to communicate with client
     * @param storeName the name of the store to be deleted
     */
    private void deleteStore(ObjectOutputStream output, String storeName) throws IOException {
        // @Ethan
        // Assume currentUser is instanceof Seller
        boolean returnObject;
        Seller currentSeller = (Seller) currentUser;
        HashMap<String, Store> sellerStores = currentSeller.getStores();
        if (sellerStores.remove(storeName) == null) { // remove store, if store does not exist, returns null.
            returnObject = false;
        } else { // if it successfully removed, returns the value of the object.
            returnObject = true;
        }
        respondToClient(output, returnObject);
    }

    /**
     * Create a new store in the {@link #currentUser}'s stores field
     * Send a TRUE boolean object to client if success
     * Send a FALSE boolean object to client if failed (i.e. seller also has the store with similar name)
     *
     * @param storeName the new store's name
     * @param output    the output stream to communicate with client
     */
    private void createStore(ObjectOutputStream output, String storeName) throws IOException {
        // @Ethan
        // Assume that currentUser is instanceof Seller.
        boolean returnObject;
        Seller currentSeller = (Seller) currentUser;
        HashMap<String, Store> sellerStores = currentSeller.getStores();
        if (sellerStores.containsKey(storeName)) {
            returnObject = false;
        } else {
            returnObject = true;
            Store newStore = new Store(storeName, currentSeller.getEmail());
            sellerStores.put(storeName, newStore);
        }
        respondToClient(output, returnObject);
    }

    /**
     * Send a string object containing the {@link #currentUser}'s usertype (customer or seller) to the client
     *
     * @param output the output stream to communicate with client
     */
    private void getUserType(ObjectOutputStream output) throws IOException {
        String userType = (currentUser instanceof Customer) ? "Customer" : "Seller";
        respondToClient(output, userType);
    }

    /**
     * Send a string object containing the {@link #currentUser}'s email to the client
     *
     * @param output the output stream to communicate with client
     */
    private void getUserEmail(ObjectOutputStream output) throws IOException {
        respondToClient(output, currentUser.getEmail());
    }

    /**
     * Send a string object containing the {@link #currentUser}'s username to the client
     *
     * @param output the output stream to communicate with client
     */
    private void getUserName(ObjectOutputStream output) throws IOException {
        respondToClient(output, currentUser.getUserName());
    }

    /**
     * Delete the currentUser from users and log out
     */
    private void deleteAccount() throws IOException {
        // @Ethan
        users.remove(currentUser.getEmail());
        logOut(); // client side should send the user to logInPage
    }

    /**
     * Modify the {@link #users}'s username
     * Send a TRUE boolean object to client if success;
     * Send a FALSE boolean object to client if failed (i.e. username is taken)
     *
     * @param output   the output stream to communicate with client
     * @param username the new username of the user
     */
    private void modifyID(ObjectOutputStream output, String username) throws IOException {
        // @Ethan

        // valid username type is checked
        boolean outputObject;
        if (users.containsKey(username)) { // username already exists.
            outputObject = false;
        } else {
            currentUser.setUserName(username);
            outputObject = true;
        }
        respondToClient(output, outputObject);
    }


    /**
     * Modify the {@link #users}'s password
     *
     * @param password the new password of the user
     */
    private void modifyPW(String password) {
        // @Ethan
        currentUser.setPassword(password);
    }

    /**
     * Set user's online status to false and close the socket
     */
    private void logOut() {
        currentUser = null; // set current user to null, in case a different user logs in before the socket is lost
    }

    /**
     * Find the user with a matching id and check for password
     * Set the {@link #currentUser} to the newly created User if success;
     * Send a  1 integer object to client if success and client is a SELLER;
     * Send a  0 integer object to client if success and client is a CUSTOMER;
     * Send a -1 integer object to client if failed (i.e. username/email/password is incorrect)
     *
     * @param output   the output stream to communicate with client
     * @param id       username of the client
     * @param password password of the client
     */
    private void logIn(ObjectOutputStream output, String id, String password) throws IOException {
        int outputInt = -1;
        for (User user : users.values()) {
            // check if User exists and the password matches, and set currentUser to user.
            // if there is no user that matches this condition, user does not exist.
            if (user.getUserName().equals(id) && user.getPassword().equals(password)) {
                currentUser = user;
                outputInt = (currentUser instanceof Customer) ? 0 : 1;
                break;
            }
        }
        respondToClient(output, outputInt);
    }

    /**
     * Create a new user in {@link #users} and set the {@link #currentUser} to the newly created User if success;
     * Send a  1 integer object to client if success;
     * Send a  0 integer object to client if email is taken
     * Send a -1 integer object to client if username is taken
     *
     * @param output   the output stream to communicate with client
     * @param type     the usertype (Customer or Seller)
     * @param username the username of the new user
     * @param email    the email of the new user
     * @param password the password of the new user
     */
    private void signUp(ObjectOutputStream output,
                        String type, String username, String email, String password) throws IOException {
        // Validate email
        User checker = users.get(email);
        if (checker != null) {
            respondToClient(output, 0);
            return;
        }
        // Validate username
        for (User user : users.values()) {
            if (user.getUserName().equals(username)) {
                respondToClient(output, -1);
                return;
            }
        }
        // User is customer
        if (type.equals("Customer")) {
            Customer newCustomer = new Customer(username, email, password);
            users.put(email, newCustomer);

            respondToClient(output, 1);
        } else if (type.equals("Seller")) { // User is seller
            Seller newSeller = new Seller(username, email, password);
            users.put(email, newSeller);

            respondToClient(output, 1);
        }
    }

    /**
     * Add current user(Customer)'s email to a seller's contactingCustomers list.
     * Send true if successful.
     * If customer email already exists in the list, send false
     *
     * @param sellerEmail the sellerEmail the customer is trying to contact.
     */
    private void contactSeller(ObjectOutputStream output, String sellerEmail) throws IOException {
        boolean noDuplicate = true;
        Seller productSeller = (Seller) users.get(sellerEmail);
        for (String contactEmail : productSeller.getContactingCustomers()) {
            if (contactEmail.equals(currentUser.getEmail())) { // check if customer has already contacted this seller.
                noDuplicate = false;
                break;
            }
        }
        // if there is no duplicate which means customer is contacting seller for the first time,
        // add customer email to seller's contactingCustomer list
        if (noDuplicate) {
            productSeller.getContactingCustomers().add(currentUser.getEmail());
        }
        respondToClient(output, noDuplicate);
    }

    /**
     * Send an ArrayList of contacted customer to client
     *
     * @param output the output stream to communicate with client
     */
    private void contactCustomer(ObjectOutputStream output) throws IOException {
        respondToClient(output, ((Seller) currentUser).getContactingCustomers());
    }

    /**
     * Process the query get from the Client GUI and call the appropriate method with appropriate parameters
     * Query will always be in the form: command_pram1_param2_..., where:
     * command is the command (used to decide which method to be called)
     * param* are the parameters for the method to be called.
     * This function only called other methods IFF the there are enough parameter to do so.
     * Print to terminal the query and method called TODO: delete after finish debugging
     *
     * @param query  the query from the Client
     * @param output the ObjectOutputStream to send object to client
     */
    private void processCommand(String query, ObjectOutputStream output) throws IOException {
        String[] queryComponents = query.split("_");

        String command = queryComponents[0];
        switch (command) {
            // Signing up (Query: SIGNUP_usertype_username_email_password)
            case "SIGNUP" -> {
                System.out.printf("Received Query: %s\n->Calling signUp()\n", query);
                signUp(output, queryComponents[1], queryComponents[2], queryComponents[3], queryComponents[4]);
            }

            // Logging in (Query: LOGIN_username/email_password)
            case "LOGIN" -> {
                System.out.printf("Received Query: %s\n->Calling logIn()\n", query);
                logIn(output, queryComponents[1], queryComponents[2]);
            }

            // Logging out (Query: LOGOUT)
            case "LOGOUT" -> {
                System.out.printf("Received Query: %s\n->Calling logOut()\n", query);
                logOut();
            }

            // Modifying username (Query: MODID_username)
            case "MODID" -> {
                System.out.printf("Received Query: %s\n->Calling modifyID()\n", query);
                modifyID(output, queryComponents[1]);
            }

            // Modifying password (Query: MODPW_password)
            case "MODPW" -> {
                System.out.printf("Received Query: %s\n->Calling modifyPW()\n", query);
                modifyPW(queryComponents[1]);
            }

            // Deleting account (Query: DELACC_userEmail)
            case "DELACC" -> {
                System.out.printf("Received Query: %s\n->Calling deleteAccount()\n", query);
                deleteAccount();
            }

            // Getting username (Query: NAME)
            case "NAME" -> {
                System.out.printf("Received Query: %s\n->Calling getUserName()\n", query);
                getUserName(output);
            }

            // Getting email (Query: EMAIL)
            case "EMAIL" -> {
                System.out.printf("Received Query: %s\n->Calling getUserEmail()\n", query);
                getUserEmail(output);
            }

            // Getting usertype (Query: TYPE)
            case "TYPE" -> {
                System.out.printf("Received Query: %s\n->Calling getUserType()\n", query);
                getUserType(output);
            }

            // Creating a store (Query: CRTSTR_storeName)
            case "CRTSTR" -> {
                System.out.printf("Received Query: %s\n->Calling createStore()\n", query);
                createStore(output, queryComponents[1]);
            }

            // Deleting a store (Query: DELSTR_storeName)
            case "DELSTR" -> {
                System.out.printf("Received Query: %s\n->Calling deleteStore()\n", query);
                deleteStore(output, queryComponents[1]);
            }

            // Getting a list of stores (can be used for searching) (Query: GETSELLSTR_searchKey)
            case "GETSELLSTR" -> {
                System.out.printf("Received Query: %s\n->Calling getSellerStores()\n", query);
                getSellerStores(output, queryComponents[1]);
            }

            // Getting a specific Store of a seller (Query: FINDSELLSTR_storeName)
            case "FINDSELLSTR" -> {
                System.out.printf("Received Query: %s\n->Calling findSellerStore()\n", query);
                findSellerStore(output, queryComponents[1]);
            }

            // Getting a store's list of products (can be used for searching)
            // (Query: GETSTRPROD_storeName_searchKey)
            case "GETSTRPROD" -> {
                System.out.printf("Received Query: %s\n->Calling getStoreProduct()\n", query);
                getStoreProduct(output, queryComponents[1], queryComponents[2]);
            }

            // Adding a new product to a store
            // (Query: ADDPROD_productName_storeName_description_price_quantity)
            case "ADDPROD" -> {
                System.out.printf("Received Query: %s\n->Calling addProduct()\n", query);
                addProduct(output, queryComponents[1], queryComponents[2], queryComponents[3],
                        queryComponents[4], queryComponents[5]);
            }

            // Modifying a store's product
            // (Query: MODPROD_productOldName_productNewName_storeName_description_price_quantity)
            case "MODPROD" -> {
                System.out.printf("Received Query: %s\n->Calling modifyProduct()\n", query);
                modifyProduct(output, queryComponents[1], queryComponents[2], queryComponents[3],
                        queryComponents[4], queryComponents[5], queryComponents[6]);

            }

            // Deleting a store's product (Query: DELPROD_storeName_productName)
            case "DELPROD" -> {
                System.out.printf("Received Query: %s\n->Calling deleteProduct()\n", query);
                deleteProduct(output, queryComponents[1], queryComponents[2]);
            }

            // Sorting market's product by price (Query: SORTP)
            case "SORTP" -> {
                System.out.printf("Received Query: %s\n->Calling sortProductPrice()\n", query);
                sortProductPrice(output);
            }

            // Sorting market's product by quantity (Query: SORTQ)
            case "SORTQ" -> {
                System.out.printf("Received Query: %s\n->Calling sortProductQty()\n", query);
                sortProductQty(output);
            }

            // Getting the market's list of products (can be used for searching) (Query: GETMRKPROD_searchKey)
            case "GETMRKPROD" -> {
                System.out.printf("Received Query: %s\n->Calling getMarketProduct()\n", query);
                getMarketProduct(output, queryComponents[1]);
            }

            // Buying a product (Query: BUY_storeName_productName_quantity)
            case "BUY" -> {
                System.out.printf("Received Query: %s\n->Calling buyItem()\n", query);
                buyItem(output, queryComponents[1], queryComponents[2], queryComponents[3]);
            }

            // Getting customer history (Query: GETHIS)
            case "GETHIS" -> {
                System.out.printf("Received Query: %s\n->Calling getPurchaseHistory()\n", query);
                getPurchaseHistory(output);
            }

            // Contacting seller of a product (Query: CNTSLR_sellerEmail)
            case "CNTSLR" -> {
                System.out.printf("Received Query: %s\n->Calling contactSeller()\n", query);
                contactSeller(output, queryComponents[1]);
            }

            // Get current user's contacted customers (Query: CNTCUS)
            case "CNTCUS" -> {
                System.out.printf("Received Query: %s\n->Calling contactCustomer()\n", query);
                contactCustomer(output);
            }

            default -> System.out.printf("Received Query: %s. ERROR!", query);
        }
    }

    private void respondToClient(ObjectOutputStream output, Object returnObj) throws IOException {
        output.writeObject(returnObj);
        output.flush();
        output.reset();
    }

    /**
     * The run method for Concurrency
     */
    public void run() {
        System.out.printf("Connection received from %s\n", socket);
        try (Scanner input = new Scanner(socket.getInputStream())) {
            try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                output.flush();
                while (input.hasNextLine()) {
                    String command = input.nextLine();
                    processCommand(command, output);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Reading in existing users
        FileIO fileIO = new FileIO();
        Server.users = fileIO.readUsers();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> fileIO.writeUsers(users), "Shutdown-thread"));

        // Allocate server socket at given port...
        try {
            ServerSocket serverSocket = new ServerSocket(8080);

            // infinite server loop: accept connection,
            // spawn thread to handle...
            while (true) {
                System.out.printf("Socket open, waiting for connections on %s\n",
                        serverSocket); // TODO: delete all print statements after debugging
                Socket socket = serverSocket.accept();
                Server server = new Server(socket);
                new Thread(server).start();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
