# BOOKSTORE — technical specification

> The original text of the specification, as sent by the customer (Serhii), is preserved verbatim.
> The wireframe sketches attached to the specification are described in text below for each screen —
> the original images were not carried over into the repository.

## Business requirements

### Role — user

When the application is opened, a login form (email,
password) must be displayed with a link to the registration form. If the user is not yet
registered in the system, then clicking the link must take them to a
form where they can enter their name, email, password, password confirmation,
date of birth and gender (all fields are mandatory), after which they must be
redirected to the main page of the application.

The user must be able to add a book to an order and place it. If at the
moment some book has run out — the text "Absent" must be displayed on it, and the add-to-order button must be disabled.
The user can go to the list of all their orders.

The user can also edit their personal information.

### Role — administrator

After authorization the user must land on the main page, from where they
can go to the list of all users of the system and books.

The administrator can delete users.

The administrator can view all books, add new books, hide
books already added to the system so that they are not shown in the list of all books for
users, and change book prices. **After a book's price is changed,
the prices of orders that were placed before the price update must not
change!**

The administrator has the ability to block users.

---

## Screens (based on the wireframe sketches)

1. **Login** — form: Email, Password, link "Register", button "LOGIN".
2. **Sign up / Register** — form: Username*, Password*, Confirm password,
   Email*, Birthday*, Gender* (the asterisk marks a mandatory field), button
   "Submit".
3. **Main (user)** — header "Hello \<username\>", menu items "Order",
   "Logout"; side menu "Profile", "Orders"; a grid of book cards (photo,
   price, a "+" button to add to the order; a book with no stock has the label "ABSENT"
   instead of the button); pagination.
4. **Orders (user)** — orders table: ID, Books, Price, Date; pagination.
5. **Profile (user)** — edit form: Username*, Password*, Confirm
   password, Email*, Birthday*, Gender*, button "Save".
6. **Main (admin)** — header "Hello \<username\>", "Logout"; side menu
   "Profile", "Users", "Books".
7. **Users (admin)** — table: Username, Gender, Email, Action (a
   delete/block icon); pagination.
8. **Books (admin)** — table: Name, Price, Photo, Count, Action (an edit
   icon "E"); button "ADD" (add a book); pagination.
9. **Book edit/add (admin)** — form: Name*, Price*, Photo (file upload),
   Visible (checkbox/toggle), button "Save".

---

## Details recorded in the specification that matter for the architecture

- **"Absent"** is tied to stock (see Image 8 — the **Count** column with numbers),
  not to an arbitrary boolean flag — this is real inventory, not just
  "available/unavailable".
- **The order price is fixed at the moment of purchase** — a direct requirement for an
  immutable price snapshot, not references to the book's current price.
- **Hiding a book ("Visible")** is a soft-hide (do not show to users), not
  deletion of the record. The admin still sees hidden books in their list.
- **Auto-login after registration** — "must be redirected to the main
  page" means the user is authorized immediately, a separate call to
  /login after registration is not provided for.
- **Blocking a user** — mentioned as a separate admin capability,
  not the same as deletion.
