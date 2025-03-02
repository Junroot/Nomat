interface NavigationContent extends React.PropsWithChildren {}

const ColumnsContainer: React.FC<NavigationContent> = ({children}) => {
     return <div
     className="w-full flex flex-row flex-nowrap overflow-auto py-[16px]"
    >
        {children}
    </div>
}

export default ColumnsContainer