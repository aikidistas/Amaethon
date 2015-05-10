package amaethon;

import uk.co.real_logic.agrona.collections.Long2ObjectHashMap;

import java.util.ArrayList;
import java.util.function.Consumer;

public class AuctionHouse implements Model
{
    private static final int INITIAL_NUMBER_OF_AUCTION_SLOTS = 16;

    private final Consumer<Auction> onAdvanceTimeFunc = this::onAdvanceTime;
    private final ArrayList<Auction> auctions = new ArrayList<>();
    private final Long2ObjectHashMap<Bidder> bidders = new Long2ObjectHashMap<>();

    private final Consumer<Auction> onNewAuctionFunc;
    private final Consumer<Auction> onNewHighBidFunc;
    private final Consumer<Auction> onFixedPriceUpdateFunc;
    private final Consumer<Auction> onAuctionOverFunc;

    private long currentTimeInNanos;

    private int activeAuctions = 0;

    public AuctionHouse(
            final Consumer<Auction> onNewAuction,
            final Consumer<Auction> onNewHighBid,
            final Consumer<Auction> onFixedPriceUpdate,
            final Consumer<Auction> onAuctionOver)
    {
        for (int i = INITIAL_NUMBER_OF_AUCTION_SLOTS - 1; i >= 0; i--)
        {
            auctions.add(new Auction());
        }

        onNewAuctionFunc = onNewAuction;
        onNewHighBidFunc = onNewHighBid;
        onFixedPriceUpdateFunc = onFixedPriceUpdate;
        onAuctionOverFunc = onAuctionOver;
    }

    public long currentTimeInNanos()
    {
        return this.currentTimeInNanos;
    }

    public int activeAuctions()
    {
        return activeAuctions;
    }

    public void advanceTime(final long now)
    {
        currentTimeInNanos = now;
        forEach(onAdvanceTimeFunc);
    }

    // TODO: use for mocking and testing mostly
    public Auction auction(final int id)
    {
        return auctions.get(id);
    }

    public int add(final byte[] name, final int nameLength, final long expiration, final long reserveValue)
    {
        final int id = findAuctionId();

        auctions.get(id).reset(id, name, nameLength, expiration, reserveValue);
        onNewAuctionFunc.accept(auctions.get(id));
        activeAuctions++;

        return id;
    }

    public int add(final byte[] name, final int nameLength, final long expiration, final long price, final int quantity)
    {
        final int id = findAuctionId();

        auctions.get(id).reset(id, name, nameLength, expiration, price, quantity);
        onNewAuctionFunc.accept(auctions.get(id));
        activeAuctions++;

        return id;
    }

    public void cancel(int auctionId)
    {
        final Auction auction = auctions.get(auctionId);

        if (null != auction)
        {
            activeAuctions--;
            auction.cancel();
        }
    }

    public boolean bid(int auctionId, long bidderId, long value)
    {
        boolean result = false;
        final Auction auction = auctions.get(auctionId);

        if (null != auction)
        {
            result = auction.bid(bidderId, value);

            if (result)
            {
                if (auction.isFixedPrice())
                {
                    onFixedPriceUpdateFunc.accept(auction);
                }
                else
                {
                    onNewHighBidFunc.accept(auction);
                }
            }
        }

        return result;
    }

    public void forEach(Consumer<Auction> consumer)
    {
        for (int i = auctions.size() - 1; i >= 0; i--)
        {
            final Auction auction = auctions.get(i);

            if (auction.isActive())
            {
                consumer.accept(auction);
            }
        }
    }

    public void addBidder(byte[] name, long bidderId, long budget)
    {
        if (null != bidders.get(bidderId))
        {
            throw new IllegalArgumentException("Bidder ID already taken: id=" + bidderId);
        }

        bidders.put(bidderId, new Bidder(name, bidderId, budget));
    }

    private void onAdvanceTime(final Auction auction)
    {
        if (auction.onAdvanceTime(currentTimeInNanos) > 0)
        {
            activeAuctions--;
            onAuctionOverFunc.accept(auction);
        }
    }

    private int findAuctionId()
    {
        int id = -1;

        for (int i = auctions.size() - 1; i >= 0; i--)
        {
            final Auction auction = auctions.get(i);

            if (auction.isInactive())
            {
                id = i;
            }
        }

        if (-1 == id)
        {
            id = auctions.size();
            auctions.add(id, new Auction());
        }

        return id;
    }
}
